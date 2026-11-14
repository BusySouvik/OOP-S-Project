(() => {
    const escapeHtml = value => String(value ?? "").replace(/[&<>"']/g, character => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[character]);

    const money = value => new Intl.NumberFormat("en-IN", {
        style: "currency", currency: "INR"
    }).format(Number(value || 0));

    async function request(url, options = {}) {
        const response = await window.vcartFetch(url, {
            headers: { "Content-Type": "application/json", ...(options.headers || {}) },
            ...options
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            throw new Error(data.message || data.error || `Request failed (${response.status})`);
        }
        return data;
    }

    async function authenticatedCustomer() {
        const response = await window.vcartFetch("/api/auth/me");
        if (response.status === 401 || response.status === 403) return null;
        if (!response.ok) throw new Error(`Account information is unavailable (${response.status}).`);
        const user = await response.json();
        return String(user.role).toUpperCase() === "CUSTOMER" ? user : null;
    }

    function safeImageUrl(value) {
        if (!value) return "";
        try {
            const url = new URL(value, location.origin);
            return url.protocol === "https:" || url.protocol === "http:" ? url.href : "";
        } catch {
            return "";
        }
    }

    async function getCustomer() {
        const user = await authenticatedCustomer();
        if (!user) {
            location.href = "/customer/login";
            throw new Error("Please sign in with a customer account.");
        }
        return user;
    }

    async function loadWishlist() {
        const container = document.getElementById("wishlist-products");
        const feedback = document.getElementById("wishlist-feedback");
        const empty = document.getElementById("wishlist-empty");
        const loading = document.getElementById("wishlist-loading");
        try {
            const user = await authenticatedCustomer();
            if (!user) {
                location.href = "/customer/login";
                return;
            }
            const cart = await request(`/api/cart/${user.id}`);
            document.querySelectorAll("[data-cart-count]").forEach(count => {
                count.textContent = cart.itemCount ?? 0;
            });
            const items = await request("/api/customer/wishlist");
            if (loading) loading.hidden = true;
            if (!items.length) {
                if (empty) empty.hidden = false;
                container.replaceChildren();
                return;
            }
            if (empty) empty.hidden = true;
            container.innerHTML = items.map(item => {
                const imageUrl = safeImageUrl(item.image_url);
                const available = Number(item.stock_quantity) > 0;
                return `
                    <article class="wishlist-card">
                        <div class="wishlist-image">
                            ${imageUrl
                                ? `<img src="${escapeHtml(imageUrl)}" alt="${escapeHtml(item.name)}" loading="lazy">`
                                : '<span aria-hidden="true">V.</span>'}
                        </div>
                        <div class="wishlist-info">
                            <h2>${escapeHtml(item.name)}</h2>
                            <p>${escapeHtml(item.description || "No product description provided.")}</p>
                            <strong>${money(item.price)}</strong>
                            <small>${available ? `${Number(item.stock_quantity)} available` : "Currently out of stock"}</small>
                            <div class="wishlist-actions">
                                <button type="button" class="shop-add-button add-wishlist-to-cart"
                                        data-product-id="${escapeHtml(item.id)}"
                                        ${available ? "" : "disabled"}>
                                    ${available ? "Add to bag" : "Sold out"}
                                </button>
                                <button type="button" class="remove-wishlist"
                                        data-remove-wishlist="${escapeHtml(item.id)}">Remove</button>
                            </div>
                        </div>
                    </article>`;
            }).join("");
            if (feedback) feedback.hidden = true;
        } catch (error) {
            if (loading) loading.hidden = true;
            if (feedback) {
                feedback.textContent = `Could not load your wishlist: ${error.message}`;
                feedback.hidden = false;
            }
        }
    }

    document.addEventListener("DOMContentLoaded", () => {
        if (document.body.dataset.page === "wishlist") {
            loadWishlist();
        }

        document.addEventListener("click", async event => {
            const saveButton = event.target.closest("[data-add-wishlist]");
            if (saveButton) {
                try {
                    await getCustomer();
                    await request(`/api/customer/wishlist/${saveButton.dataset.addWishlist}`, {
                        method: "POST"
                    });
                    saveButton.textContent = "♥ Saved";
                    saveButton.classList.add("is-saved");
                } catch (error) {
                    if (error.message !== "Please sign in with a customer account.") {
                        window.alert(error.message);
                    }
                }
                return;
            }

            const removeButton = event.target.closest("[data-remove-wishlist]");
            if (removeButton) {
                removeButton.disabled = true;
                try {
                    await getCustomer();
                    await request(`/api/customer/wishlist/${removeButton.dataset.removeWishlist}`, {
                        method: "DELETE"
                    });
                    await loadWishlist();
                } catch (error) {
                    removeButton.disabled = false;
                    window.alert(error.message);
                }
                return;
            }

            const addButton = event.target.closest(".add-wishlist-to-cart");
            if (addButton) {
                addButton.disabled = true;
                try {
                    const user = await getCustomer();
                    await request(`/api/cart/${user.id}/items`, {
                        method: "POST",
                        body: JSON.stringify({
                            productId: Number(addButton.dataset.productId),
                            quantity: 1
                        })
                    });
                    const cart = await request(`/api/cart/${user.id}`);
                    document.querySelectorAll("[data-cart-count]").forEach(count => {
                        count.textContent = cart.itemCount ?? 0;
                    });
                    addButton.textContent = "Added ✓";
                    window.setTimeout(() => {
                        addButton.textContent = "Add to bag";
                        addButton.disabled = false;
                    }, 1000);
                } catch (error) {
                    addButton.disabled = false;
                    window.alert(error.message);
                }
            }
        });
    });
})();

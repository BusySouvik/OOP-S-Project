(() => {
    const pageSize = 12;

    async function requestJson(url, options = {}) {
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

    async function loadUser() {
        const response = await window.vcartFetch("/api/auth/me");
        if (response.status === 401 || response.status === 403) return null;
        if (!response.ok) throw new Error(`Account information is unavailable (${response.status}).`);
        return response.json();
    }

    const escapeHtml = value => String(value ?? "").replace(/[&<>"']/g, character => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[character]);

    const money = value => new Intl.NumberFormat("en-IN", {
        style: "currency", currency: "INR"
    }).format(Number(value || 0));

    function safeImageUrl(value) {
        if (!value) return "";
        try {
            const url = new URL(value, location.origin);
            return url.protocol === "https:" || url.protocol === "http:" ? url.href : "";
        } catch {
            return "";
        }
    }

    function productCard(product) {
        const imageUrl = safeImageUrl(product.imageUrl);
        const available = Number(product.stockQuantity) > 0;
        const image = imageUrl
            ? `<img src="${escapeHtml(imageUrl)}" alt="${escapeHtml(product.name)}" loading="lazy">`
            : '<div class="shop-product-placeholder" aria-hidden="true"><span>V.</span></div>';

        return `
            <article class="shop-product-card" data-product-id="${escapeHtml(product.id)}">
                <div class="shop-product-image">
                    ${image}
                    <span class="shop-product-tag">${available ? "AVAILABLE NOW" : "SOLD OUT"}</span>
                </div>
                <div class="shop-product-info">
                    <div class="shop-product-meta">
                        <span>${escapeHtml(product.categoryName || "Uncategorised")}</span>
                        <span>SKU ${escapeHtml(product.sku)}</span>
                    </div>
                    <h3>${escapeHtml(product.name)}</h3>
                    <p>${escapeHtml(product.description || "No product description provided.")}</p>
                    <div class="shop-product-buy">
                        <strong>${money(product.price)}</strong>
                        <button type="button" class="shop-add-button add-to-cart"
                                data-product-id="${escapeHtml(product.id)}"
                                data-stock="${Number(product.stockQuantity)}" ${available ? "" : "disabled"}>
                            <span>${available ? "Add to bag" : "Sold out"}</span><b>&rarr;</b>
                        </button>
                    </div>
                    <p class="shop-stock-note">${available ? `${Number(product.stockQuantity)} available` : "Currently out of stock"}</p>
                    <button type="button" class="shop-save-button"
                            data-add-wishlist="${escapeHtml(product.id)}">♡ Save to wishlist</button>
                    <button type="button" class="shop-product-details-button"
                            data-product-details="${escapeHtml(product.id)}">View product details</button>
                </div>
            </article>`;
    }

    document.addEventListener("DOMContentLoaded", async () => {
        const body = document.body;
        if (body.dataset.page !== "storefront") return;

        const sessionActions = document.getElementById("customer-session-actions");
        const guestActions = document.getElementById("guest-actions");
        const profileMenu = document.getElementById("profile-menu");
        const profileToggle = document.getElementById("profile-toggle");
        const accountError = document.getElementById("account-error");
        const cartLink = document.querySelector(".shop-cart-link");
        const roleLinks = document.querySelectorAll("[data-customer-only]");
        let user = null;

        try {
            user = await loadUser();
        } catch (error) {
            const feedback = document.getElementById("catalogue-feedback");
            if (accountError) {
                accountError.textContent = error.message;
                accountError.hidden = false;
            }
            if (feedback) {
                feedback.textContent = `Account details could not be loaded: ${error.message}`;
                feedback.hidden = false;
            }
        }

        const role = String(user?.role || "").toUpperCase();
        if (user) {
            if (guestActions) guestActions.hidden = true;
            if (sessionActions) sessionActions.hidden = false;
            const name = user.full_name || user.email || "My account";
            const accountLink = document.getElementById("account-link");
            const profileName = document.getElementById("profile-name");
            const profileEmail = document.getElementById("profile-email");
            const profileRole = document.getElementById("profile-role");
            if (accountLink) accountLink.textContent = role === "CUSTOMER" ? name : role.toLowerCase();
            if (profileName) profileName.textContent = name;
            if (profileEmail) profileEmail.textContent = user.email || "";
            if (profileRole) profileRole.textContent = role;
            roleLinks.forEach(link => { link.hidden = role !== "CUSTOMER"; });
            const ownerLink = document.getElementById("owner-dashboard-link");
            const staffLink = document.getElementById("staff-dashboard-link");
            if (ownerLink) ownerLink.hidden = role !== "ADMIN";
            if (staffLink) staffLink.hidden = role !== "STAFF";
            if (cartLink) cartLink.hidden = role !== "CUSTOMER";

            const greeting = document.getElementById("customer-greeting");
            if (greeting && role === "CUSTOMER" && user.full_name) {
                greeting.textContent = `, ${user.full_name}`;
            }
        }

        if (body.dataset.customerHome === "true" && role !== "CUSTOMER") {
            if (role === "ADMIN") location.replace("/owner/dashboard");
            else if (role === "STAFF") location.replace("/staff/dashboard");
            else location.replace("/customer/login");
            return;
        }

        if (profileToggle && profileMenu) {
            profileToggle.addEventListener("click", () => {
                const open = profileMenu.hidden;
                profileMenu.hidden = !open;
                profileToggle.setAttribute("aria-expanded", String(open));
            });
            document.addEventListener("click", event => {
                if (!event.target.closest(".shop-account-wrap")) {
                    profileMenu.hidden = true;
                    profileToggle.setAttribute("aria-expanded", "false");
                }
            });
            document.addEventListener("keydown", event => {
                if (event.key === "Escape") {
                    profileMenu.hidden = true;
                    profileToggle.setAttribute("aria-expanded", "false");
                }
            });
        }

        document.querySelectorAll("[data-logout]").forEach(button => {
            button.addEventListener("click", async () => {
                button.disabled = true;
                try {
                    await requestJson("/api/auth/logout", { method: "POST" });
                    profileMenu.hidden = true;
                    location.href = role === "ADMIN" ? "/owner/login"
                        : role === "STAFF" ? "/staff/login" : "/customer/login";
                } catch (error) {
                    button.disabled = false;
                    if (accountError) {
                        accountError.textContent = `Could not log out: ${error.message}`;
                        accountError.hidden = false;
                    } else {
                        window.alert(`Could not log out: ${error.message}`);
                    }
                }
            });
        });

        const greeting = document.getElementById("customer-greeting");
        if (user && role === "CUSTOMER") {
            try {
                const cart = await requestJson(`/api/cart/${user.id}`);
                document.querySelectorAll("[data-cart-count]").forEach(count => {
                    count.textContent = cart.itemCount ?? 0;
                });
            } catch (error) {
                const feedback = document.getElementById("catalogue-feedback");
                if (feedback) {
                    feedback.textContent = `Your cart could not be loaded: ${error.message}`;
                    feedback.hidden = false;
                }
            }
        }

        setupCatalogue();
        setupProductActions(() => user);
    });

    function setupCatalogue() {
        const grid = document.getElementById("product-grid");
        const form = document.getElementById("catalogue-filter-form");
        const feedback = document.getElementById("catalogue-feedback");
        const emptyState = document.getElementById("catalogue-status");
        const noResults = document.getElementById("shop-no-results");
        const count = document.getElementById("visible-product-count");
        const loadMore = document.getElementById("load-more-products");
        const search = document.getElementById("product-search");
        if (!grid || !form) return;

        let offset = grid.children.length;
        let sequence = 0;
        let debounce;
        if (count) count.textContent = `${offset} products shown`;
        if (emptyState) emptyState.hidden = offset !== 0;
        if (loadMore) loadMore.hidden = offset < pageSize;

        const paramsFor = pageOffset => {
            const params = new URLSearchParams();
            const keyword = search.value.trim();
            const category = document.getElementById("category-filter").value;
            const minimum = document.getElementById("minimum-price").value;
            const maximum = document.getElementById("maximum-price").value;
            const availability = document.getElementById("availability-filter").value;
            if (keyword) params.set("search", keyword);
            if (category) params.set("categoryId", category);
            if (minimum) params.set("minPrice", minimum);
            if (maximum) params.set("maxPrice", maximum);
            if (availability) params.set("inStock", availability);
            params.set("sort", document.getElementById("product-sort").value);
            params.set("limit", String(pageSize));
            params.set("offset", String(pageOffset));
            return params;
        };

        const fetchProducts = async append => {
            const currentSequence = ++sequence;
            const nextOffset = append ? offset : 0;
            if (!append) offset = 0;
            if (feedback) {
                feedback.textContent = append ? "Loading more products…" : "Updating catalogue…";
                feedback.hidden = false;
            }
            if (loadMore) loadMore.disabled = true;
            try {
                const params = paramsFor(nextOffset);
                const products = await requestJson(`/api/products?${params.toString()}`);
                if (currentSequence !== sequence) return;
                if (!append) {
                    grid.replaceChildren();
                    offset = 0;
                }
                products.forEach(product => grid.insertAdjacentHTML("beforeend", productCard(product)));
                offset += products.length;
                if (count) count.textContent = `${offset} product${offset === 1 ? "" : "s"} shown`;
                if (emptyState) emptyState.hidden = true;
                if (noResults) noResults.hidden = grid.children.length !== 0;
                if (loadMore) {
                    loadMore.hidden = products.length < pageSize;
                    loadMore.disabled = false;
                }
                if (feedback) feedback.hidden = true;
            } catch (error) {
                if (currentSequence !== sequence) return;
                if (feedback) {
                    feedback.textContent = `Could not load products: ${error.message}`;
                    feedback.hidden = false;
                }
                if (loadMore) {
                    loadMore.disabled = false;
                    loadMore.hidden = true;
                }
            }
        };

        const updateCategorySelection = () => {
            const selectedCategory = document.getElementById("category-filter").value;
            document.querySelectorAll("[data-category-filter]").forEach(category => {
                if (selectedCategory === category.dataset.categoryFilter) {
                    category.setAttribute("aria-current", "true");
                } else {
                    category.removeAttribute("aria-current");
                }
            });
        };

        form.addEventListener("submit", event => {
            event.preventDefault();
            const minimum = document.getElementById("minimum-price").value;
            const maximum = document.getElementById("maximum-price").value;
            if (minimum && maximum && Number(minimum) > Number(maximum)) {
                if (feedback) {
                    feedback.textContent = "Minimum price cannot exceed maximum price.";
                    feedback.hidden = false;
                }
                return;
            }
            fetchProducts(false);
        });

        search.addEventListener("input", () => {
            window.clearTimeout(debounce);
            debounce = window.setTimeout(() => form.requestSubmit(), 300);
        });
        ["category-filter", "availability-filter", "product-sort"].forEach(id => {
            document.getElementById(id).addEventListener("change", () => {
                updateCategorySelection();
                form.requestSubmit();
            });
        });
        ["minimum-price", "maximum-price"].forEach(id => {
            document.getElementById(id).addEventListener("change", () => form.requestSubmit());
        });

        document.getElementById("clear-filters").addEventListener("click", () => {
            form.reset();
            fetchProducts(false);
        });
        document.querySelectorAll("[data-category-filter]").forEach(category => {
            category.addEventListener("click", event => {
                event.preventDefault();
                document.getElementById("category-filter").value = category.dataset.categoryFilter;
                updateCategorySelection();
                form.requestSubmit();
                document.getElementById("products").scrollIntoView({ behavior: "smooth" });
            });
        });
        if (loadMore) loadMore.addEventListener("click", () => fetchProducts(true));
    }

    function setupProductActions(getUser) {
        document.addEventListener("click", async event => {
            const addButton = event.target.closest(".add-to-cart");
            if (addButton) {
                event.preventDefault();
                const user = getUser();
                if (!user || String(user.role).toUpperCase() !== "CUSTOMER") {
                    location.href = "/customer/login";
                    return;
                }
                const quantityInput = addButton.closest("#product-details-dialog")
                    ?.querySelector("#detail-quantity");
                const quantity = quantityInput ? Number(quantityInput.value) : 1;
                const stock = Number(addButton.dataset.stock || 0);
                if (!Number.isInteger(quantity) || quantity < 1 || quantity > stock) {
                    window.alert("Enter a quantity within the available stock.");
                    return;
                }
                const label = addButton.querySelector("span");
                const original = label?.textContent || addButton.textContent;
                addButton.disabled = true;
                try {
                    await requestJson(`/api/cart/${user.id}/items`, {
                        method: "POST",
                        body: JSON.stringify({
                            productId: Number(addButton.dataset.productId),
                            quantity
                        })
                    });
                    const stockAfterAdd = Math.max(0, Number(addButton.dataset.stock || 0) - quantity);
                    addButton.dataset.stock = String(stockAfterAdd);
                    const card = Array.from(document.querySelectorAll(".shop-product-card"))
                        .find(item => item.dataset.productId === addButton.dataset.productId);
                    if (card) {
                        const cardButton = card.querySelector(".add-to-cart");
                        const tag = card.querySelector(".shop-product-tag");
                        const stockNote = card.querySelector(".shop-stock-note");
                        if (cardButton) {
                            cardButton.dataset.stock = String(stockAfterAdd);
                            cardButton.disabled = stockAfterAdd === 0;
                        }
                        if (tag) tag.textContent = stockAfterAdd > 0 ? "AVAILABLE NOW" : "SOLD OUT";
                        if (stockNote) stockNote.textContent = stockAfterAdd > 0
                            ? `${stockAfterAdd} available` : "Currently out of stock";
                    }
                    if (label) label.textContent = "Added ✓";
                    if (stockAfterAdd === 0) addButton.disabled = true;
                    try {
                        const cart = await requestJson(`/api/cart/${user.id}`);
                        document.querySelectorAll("[data-cart-count]").forEach(count => {
                            count.textContent = cart.itemCount ?? 0;
                        });
                    } catch (error) {
                        const feedback = document.getElementById("catalogue-feedback");
                        if (feedback) {
                            feedback.textContent = `Added to your bag, but the count could not be refreshed: ${error.message}`;
                            feedback.hidden = false;
                        }
                    }
                    window.setTimeout(() => {
                        if (label) label.textContent = original;
                        if (stockAfterAdd > 0) {
                            addButton.disabled = false;
                        }
                    }, 1000);
                } catch (error) {
                    addButton.disabled = false;
                    window.alert(error.message);
                }
                return;
            }

            const detailsButton = event.target.closest("[data-product-details]");
            if (!detailsButton) return;
            const dialog = document.getElementById("product-details-dialog");
            const content = document.getElementById("product-details-content");
            try {
                const product = await requestJson(`/api/products/${detailsButton.dataset.productDetails}`);
                const imageUrl = safeImageUrl(product.imageUrl);
                const available = Number(product.stockQuantity) > 0;
                content.innerHTML = `
                    ${imageUrl
                        ? `<img class="shop-dialog-image" src="${escapeHtml(imageUrl)}" alt="${escapeHtml(product.name)}">`
                        : ""}
                    <span class="shop-kicker">PRODUCT DETAILS</span>
                    <p class="shop-dialog-category">${escapeHtml(product.categoryName || "Uncategorised")}</p>
                    <h2 id="product-detail-title">${escapeHtml(product.name)}</h2>
                    <p>${escapeHtml(product.description || "No product description provided.")}</p>
                    <p>SKU: ${escapeHtml(product.sku)}</p>
                    <p>${money(product.price)} · ${available ? `${Number(product.stockQuantity)} available` : "Out of stock"}</p>
                    <label class="shop-detail-quantity">Quantity
                        <input id="detail-quantity" type="number" min="1"
                               max="${Math.max(Number(product.stockQuantity), 1)}" value="1"
                               ${available ? "" : "disabled"}>
                    </label>
                    <button type="button" class="shop-add-button add-to-cart"
                            data-product-id="${escapeHtml(product.id)}"
                            data-stock="${Number(product.stockQuantity)}" ${available ? "" : "disabled"}>
                        <span>${available ? "Add to bag" : "Sold out"}</span><b>&rarr;</b>
                    </button>
                    <button type="button" class="shop-save-button"
                            data-add-wishlist="${escapeHtml(product.id)}">♡ Save to wishlist</button>
                `;
                dialog.showModal();
            } catch (error) {
                window.alert(error.message);
            }
        });

        document.getElementById("product-details-dialog")?.addEventListener("click", event => {
            if (event.target === event.currentTarget) event.currentTarget.close();
        });
    }
})();

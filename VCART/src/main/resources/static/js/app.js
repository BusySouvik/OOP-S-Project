const money = value => new Intl.NumberFormat("en-IN", {
    style: "currency", currency: "INR"
}).format(Number(value || 0));

async function api(url, options = {}) {
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

async function currentUser() {
    try {
        return await api("/api/auth/me");
    } catch {
        return null;
    }
}

function showMessage(element, message, error = false) {
    if (!element) return;
    element.textContent = message;
    element.className = error ? "form-message error" : "form-message success";
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

function formatDate(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "Date unavailable" : date.toLocaleString();
}

document.addEventListener("DOMContentLoaded", async () => {
    const isStorefront = document.body.dataset.page === "storefront";
    const user = isStorefront ? null : await currentUser();
    if (!isStorefront) {
        document.querySelectorAll("[data-logout]").forEach(button => {
            button.addEventListener("click", async () => {
                try {
                    await api("/api/auth/logout", { method: "POST" });
                    const role = String(user?.role || "").toUpperCase();
                    location.href = role === "ADMIN" ? "/owner/login"
                        : role === "STAFF" ? "/staff/login" : "/customer/login";
                } catch (error) {
                    alert(error.message);
                }
            });
        });
    }

    const loginForm = document.getElementById("login-form");
    if (loginForm) {
        loginForm.addEventListener("submit", async event => {
            event.preventDefault();
            const message = document.getElementById("form-message");
            const body = Object.fromEntries(new FormData(loginForm));

            try {
                const portal = loginForm.dataset.loginRole || "customer";
                const user = await api(`/api/auth/login/${portal}`, {
                    method: "POST",
                    body: JSON.stringify(body)
                });

                const role = String(user.role).toUpperCase();
                location.href = role === "ADMIN" ? "/owner/dashboard"
                    : role === "STAFF" ? "/staff/dashboard" : "/customer/dashboard";
            } catch (error) {
                showMessage(message, error.message, true);
            }
        });
    }

    const registerForm = document.getElementById("register-form");
    if (registerForm) {
        registerForm.addEventListener("submit", async event => {
            event.preventDefault();
            const message = document.getElementById("form-message");
            const body = Object.fromEntries(new FormData(registerForm));

            try {
                await api("/api/auth/register", {
                    method: "POST",
                    body: JSON.stringify(body)
                });
                showMessage(message, "Account created. Redirecting to sign in...");
                setTimeout(() => location.href = "/customer/login", 1200);
            } catch (error) {
                showMessage(message, error.message, true);
            }
        });
    }

    document.querySelector(".shop-dialog-close")?.addEventListener("click", () => {
        document.getElementById("product-details-dialog").close();
    });

    if (document.body.dataset.page === "cart") await loadCart();
    if (document.body.dataset.page === "checkout") await loadCheckout();
    if (document.body.dataset.page === "orders") await loadOrders();

    const applyButton = document.getElementById("apply-coupon");
    if (applyButton) {
        applyButton.addEventListener("click", validateCoupon);
    }

    const checkoutForm = document.getElementById("checkout-form");
    if (checkoutForm) {
        checkoutForm.addEventListener("submit", placeOrder);
    }
});

async function requireUser() {
    const user = await currentUser();
    if (!user) {
        location.href = "/customer/login";
        throw new Error("Please sign in.");
    }
    return user;
}

async function loadCart() {
    const user = await requireUser();
    const cart = await api(`/api/cart/${user.id}`);
    const container = document.getElementById("cart-items");

    document.querySelectorAll("[data-cart-count], #cart-count").forEach(count => {
        count.textContent = cart.itemCount;
    });
    document.getElementById("cart-subtotal").textContent = money(cart.total);
    document.getElementById("cart-total").textContent = money(cart.total);

    if (!cart.items || cart.items.length === 0) {
        container.innerHTML = '<div class="empty-state"><h2>Your cart is waiting.</h2><p>Discover something you love and add it here.</p><a class="primary-button" href="/customer/dashboard#products">Explore products</a></div>';
        return;
    }

    container.innerHTML = cart.items.map(item => `
        <article class="cart-item">
            <div class="cart-item-art">${safeImageUrl(item.imageUrl)
                ? `<img src="${escapeHtml(safeImageUrl(item.imageUrl))}" alt="${escapeHtml(item.productName)}">`
                : "V."}</div>
            <div class="cart-item-info">
                <h3>${escapeHtml(item.productName)}</h3>
                <p>${money(item.price)} each</p>
                <small>${item.availableStock} available</small>
                <div class="quantity-controls">
                    <button data-change="-1" data-id="${item.id}">−</button>
                    <strong>${item.quantity}</strong>
                    <button data-change="1" data-id="${item.id}"
                            ${item.quantity >= item.availableStock ? "disabled" : ""}>+</button>
                </div>
            </div>
            <div class="cart-item-end">
                <strong>${money(Number(item.price) * item.quantity)}</strong>
                <button class="remove-button" data-remove="${item.id}">Remove</button>
            </div>
        </article>
    `).join("");

    container.querySelectorAll("[data-change]").forEach(button => {
        button.addEventListener("click", async () => {
            const item = cart.items.find(x => x.id == button.dataset.id);
            const quantity = item.quantity + Number(button.dataset.change);
            if (quantity < 1) return;
            try {
                await api(`/api/cart/${user.id}/items/${item.id}`, {
                    method: "PATCH",
                    body: JSON.stringify({ quantity })
                });
                await loadCart();
            } catch (error) { alert(error.message); }
        });
    });

    container.querySelectorAll("[data-remove]").forEach(button => {
        button.addEventListener("click", async () => {
            try {
                await api(`/api/cart/${user.id}/items/${button.dataset.remove}`, {
                    method: "DELETE"
                });
                await loadCart();
            } catch (error) { alert(error.message); }
        });
    });
}

let checkoutCart = null;
let appliedCoupon = "";

async function loadCheckout() {
    const user = await requireUser();
    checkoutCart = await api(`/api/cart/${user.id}`);

    if (!checkoutCart.items || checkoutCart.items.length === 0) {
        location.href = "/cart";
        return;
    }

    document.getElementById("checkout-items").innerHTML =
        checkoutCart.items.map(item =>
            `<p class="summary-line"><span>${escapeHtml(item.productName)} × ${item.quantity}</span><strong>${money(Number(item.price) * item.quantity)}</strong></p>`
        ).join("");

    document.getElementById("checkout-subtotal").textContent = money(checkoutCart.total);
    document.getElementById("checkout-total").textContent = money(checkoutCart.total);
}

async function validateCoupon() {
    const code = document.getElementById("coupon-code").value.trim();
    const message = document.getElementById("coupon-message");
    const user = await requireUser();

    try {
        if (!checkoutCart) checkoutCart = await api(`/api/cart/${user.id}`);
        const result = await api("/api/discounts/validate", {
            method: "POST",
            body: JSON.stringify({ code, subtotal: checkoutCart.total })
        });

        appliedCoupon = result.code;
        document.getElementById("checkout-discount").textContent = money(result.discount);
        document.getElementById("checkout-total").textContent = money(result.total);
        showMessage(message, `Coupon applied. You save ${money(result.discount)}.`);
    } catch (error) {
        appliedCoupon = "";
        document.getElementById("checkout-discount").textContent = money(0);
        document.getElementById("checkout-total").textContent = money(checkoutCart?.total);
        showMessage(message, error.message, true);
    }
}

async function placeOrder(event) {
    event.preventDefault();
    const user = await requireUser();
    const message = document.getElementById("form-message");
    const address = new FormData(event.currentTarget).get("shippingAddress");
    const submitButton = event.currentTarget.querySelector('button[type="submit"]');
    if (submitButton?.disabled) return;
    if (submitButton) submitButton.disabled = true;

    try {
        const result = await api(`/api/orders/checkout/${user.id}`, {
            method: "POST",
            body: JSON.stringify({
                shippingAddress: address,
                couponCode: appliedCoupon
            })
        });

        let payment;
        try {
            payment = await api(`/api/payments/${user.id}/orders/${result.orderId}/process`, {
                method: "POST"
            });
        } catch (error) {
            showMessage(message,
                `Order ${result.orderNumber} was created, but payment is not complete. Retry from Orders. ${error.message}`,
                true);
            setTimeout(() => location.href = "/orders", 1800);
            return;
        }
        showMessage(message, `Order ${result.orderNumber}: ${payment.message}`);
        setTimeout(() => location.href = "/orders", 1600);
    } catch (error) {
        showMessage(message, error.message, true);
        if (submitButton) submitButton.disabled = false;
    }
}

async function loadOrders() {
    const user = await requireUser();
    const orders = await api(`/api/orders/${user.id}`);
    const container = document.getElementById("orders-list");

    if (!orders.length) {
        container.innerHTML = '<div class="empty-state"><h2>No orders yet.</h2><p>Your next favourite purchase starts here.</p><a href="/customer/dashboard#products" class="primary-button">Explore VCART</a></div>';
        return;
    }

    container.innerHTML = orders.map(order => `
        <article class="order-card">
            <div><span class="eyebrow">ORDER ${escapeHtml(order.order_number)}</span><h3>${money(order.total_amount)}</h3><p>${formatDate(order.created_at)}</p><p>${escapeHtml(order.shipping_address || "")}</p></div>
            <div class="order-status"><span>${escapeHtml(order.status)}</span><small>${escapeHtml(order.payment_status)}${order.payment_provider === "MOCK" ? " · MOCK, NO MONEY MOVED" : order.payment_provider === "LEGACY" ? " · LEGACY, UNVERIFIED" : order.payment_provider ? ` · ${escapeHtml(order.payment_provider)}` : ""}</small>
                <small>${escapeHtml(order.refund_status || "NO REFUND REQUEST")}${order.refund_status === "PROCESSED" && order.payment_provider === "MOCK" ? " · SIMULATED, NO MONEY MOVED" : ""}</small></div>
            <div>
                <button class="outline-button" data-details="${escapeHtml(order.id)}">View details</button>
                ${order.status === "PLACED" && order.payment_status === "PENDING" && order.payment_provider === "MOCK" && order.payment_attempt_status !== "PROCESSING"
                    ? `<button class="outline-button" data-pay="${escapeHtml(order.id)}">Retry development payment</button>` : ""}
                ${order.status === "PLACED" && order.payment_status === "PENDING" && order.payment_provider === "LEGACY"
                    ? `<small>Legacy payment is unverified and cannot be retried with the development provider.</small>` : ""}
                ${order.status === "PLACED" && order.payment_status === "PENDING" && order.payment_attempt_status !== "PROCESSING"
                    ? `<button class="outline-button" data-cancel="${escapeHtml(order.id)}">Cancel order</button>` : ""}
                ${order.payment_status === "PAID" && !order.refund_status
                    ? `<button class="outline-button" data-refund="${escapeHtml(order.id)}">Request refund</button>` : ""}
            </div>
            <div class="order-detail" data-order-details="${escapeHtml(order.id)}" hidden></div>
        </article>
    `).join("");

    container.querySelectorAll("[data-details]").forEach(button => {
        button.addEventListener("click", async () => {
            const detail = container.querySelector(`[data-order-details="${button.dataset.details}"]`);
            if (!detail.hidden) {
                detail.hidden = true;
                return;
            }
            try {
                const result = await api(`/api/orders/${user.id}/${button.dataset.details}`);
                const order = result.order || {};
                detail.innerHTML = `
                    <p><span>Placed</span><strong>${formatDate(order.created_at)}</strong></p>
                    <p><span>Order status</span><strong>${escapeHtml(order.status || "Unavailable")}</strong></p>
                    <p><span>Payment status</span><strong>${escapeHtml(order.payment_status || "Unavailable")}</strong></p>
                    ${result.items.map(item => `
                    <p>${escapeHtml(item.product_name)} × ${item.quantity}
                       <strong>${money(item.line_total)}</strong></p>
                `).join("") || "<p>No item details available.</p>"}`;
                detail.hidden = false;
            } catch (error) {
                alert(error.message);
            }
        });
    });

    container.querySelectorAll("[data-cancel]").forEach(button => {
        button.addEventListener("click", async () => {
            if (!confirm("Cancel this order?")) return;
            try {
                await api(`/api/orders/${user.id}/${button.dataset.cancel}/cancel`, {
                    method: "PATCH"
                });
                await loadOrders();
            } catch (error) { alert(error.message); }
        });
    });

    container.querySelectorAll("[data-pay]").forEach(button => {
        button.addEventListener("click", async () => {
            button.disabled = true;
            try {
                const payment = await api(`/api/payments/${user.id}/orders/${button.dataset.pay}/process`, {
                    method: "POST"
                });
                alert(payment.message);
                await loadOrders();
            } catch (error) {
                alert(error.message);
                button.disabled = false;
            }
        });
    });

    container.querySelectorAll("[data-refund]").forEach(button => {
        button.addEventListener("click", async () => {
            const reason = prompt("Why are you requesting a refund?");
            if (reason === null) return;
            button.disabled = true;
            try {
                await api(`/api/refunds/${user.id}/orders/${button.dataset.refund}`, {
                    method: "POST",
                    body: JSON.stringify({ reason })
                });
                await loadOrders();
            } catch (error) {
                alert(error.message);
                button.disabled = false;
            }
        });
    });
}

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, char => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;",
        '"': "&quot;", "'": "&#39;"
    })[char]);
}
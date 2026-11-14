const staffEsc = value => String(value ?? "").replace(/[&<>"']/g, char => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
})[char]);
const staffMoney = value => "₹" + Number(value || 0).toLocaleString("en-IN", { maximumFractionDigits: 2 });
const staffMessage = text => { document.getElementById("staff-message").textContent = text; };

async function staffApi(url, options = {}) {
    const response = await window.vcartFetch(url, {
        ...options,
        headers: { "Content-Type": "application/json", ...(options.headers || {}) }
    });
    const text = await response.text();
    let data = {};
    try { data = text ? JSON.parse(text) : {}; } catch {}
    if (!response.ok) throw new Error(data.message || data.error || text || "Request failed.");
    return data;
}

async function loadStaffOrders() {
    const orders = await staffApi("/api/staff/orders");
    const body = document.getElementById("staff-orders");
    body.innerHTML = orders.length ? orders.map(order => `
        <tr>
            <td>${staffEsc(order.order_number)}</td>
            <td>${staffEsc(order.full_name)}</td>
            <td>${staffEsc(order.shipping_address)}</td>
            <td>${staffMoney(order.total_amount)}</td>
            <td>${staffEsc(order.status)}</td>
            <td><select data-status-for="${staffEsc(order.id)}">
                ${["PROCESSING", "SHIPPED", "DELIVERED"].map(status =>
                    `<option value="${status}" ${status === order.status ? "selected" : ""}>${status}</option>`
                ).join("")}
            </select><button class="small-action" data-save-order="${staffEsc(order.id)}">Save</button></td>
        </tr>`).join("") : '<tr><td colspan="6">No paid orders need fulfilment.</td></tr>';
}

async function loadStaffSupport() {
    const tickets = await staffApi("/api/staff/support");
    const body = document.getElementById("staff-support");
    body.innerHTML = tickets.length ? tickets.map(ticket => `
        <tr>
            <td>${staffEsc(ticket.subject)}</td>
            <td>${staffEsc(ticket.message)}</td>
            <td>${staffEsc(ticket.full_name || ticket.email || "Former customer")}</td>
            <td>${staffEsc(ticket.status)}</td>
            <td><select data-support-status="${staffEsc(ticket.id)}">
                ${["OPEN", "IN_PROGRESS", "RESOLVED"].map(status =>
                    `<option value="${status}" ${status === ticket.status ? "selected" : ""}>${status}</option>`
                ).join("")}
            </select><button class="small-action" data-save-support="${staffEsc(ticket.id)}">Save</button></td>
        </tr>`).join("") : '<tr><td colspan="5">No support requests.</td></tr>';
}

document.addEventListener("DOMContentLoaded", async () => {
    try {
        const me = await staffApi("/api/auth/me");
        if (String(me.role).toUpperCase() !== "STAFF") {
            location.href = "/staff/login";
            return;
        }
        await Promise.all([loadStaffOrders(), loadStaffSupport()]);
    } catch (error) {
        staffMessage(error.message);
        if (error.message.includes("sign in") || error.message.includes("Not logged")) {
            location.href = "/staff/login";
        }
    }

    document.getElementById("staff-orders").addEventListener("click", async event => {
        const button = event.target.closest("[data-save-order]");
        if (!button) return;
        const orderId = button.dataset.saveOrder;
        const status = document.querySelector(`[data-status-for="${CSS.escape(orderId)}"]`).value;
        try {
            await staffApi(`/api/staff/orders/${orderId}/status`, {
                method: "PATCH", body: JSON.stringify({ status })
            });
            await loadStaffOrders();
            staffMessage("Order fulfilment status updated.");
        } catch (error) { staffMessage(error.message); }
    });

    document.getElementById("staff-support").addEventListener("click", async event => {
        const button = event.target.closest("[data-save-support]");
        if (!button) return;
        const ticketId = button.dataset.saveSupport;
        const status = document.querySelector(`[data-support-status="${CSS.escape(ticketId)}"]`).value;
        try {
            await staffApi(`/api/staff/support/${ticketId}/status`, {
                method: "PATCH", body: JSON.stringify({ status })
            });
            await loadStaffSupport();
            staffMessage("Support status updated.");
        } catch (error) { staffMessage(error.message); }
    });

    document.getElementById("staff-logout").addEventListener("click", async () => {
        try {
            await staffApi("/api/auth/logout", { method: "POST" });
            location.href = "/staff/login";
        } catch (error) { staffMessage(error.message); }
    });
});

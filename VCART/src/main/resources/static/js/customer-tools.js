(() => {
    const request = async (url, options = {}) => {
        const response = await window.vcartFetch(url, {
            headers: { "Content-Type": "application/json", ...(options.headers || {}) },
            ...options
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            throw new Error(data.message || data.error || `Request failed (${response.status})`);
        }
        return data;
    };

    const escapeHtml = value => String(value ?? "").replace(/[&<>"']/g, character => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[character]);

    const show = (element, message, error = false) => {
        if (!element) return;
        element.textContent = message;
        element.classList.toggle("error", error);
        element.hidden = false;
    };

    async function loadSupport() {
        const container = document.getElementById("support-items");
        try {
            const items = await request("/api/customer/support");
            container.innerHTML = items.length ? items.map(item => `
                <article class="support-item">
                    <strong>${escapeHtml(item.subject)}</strong>
                    <p>${escapeHtml(item.message)}</p>
                    <small>${escapeHtml(item.status)} · ${new Date(item.created_at).toLocaleString()}</small>
                </article>`).join("") : '<p class="tool-note">No support requests yet.</p>';
        } catch (error) {
            container.textContent = `Could not load requests: ${error.message}`;
        }
    }

    document.addEventListener("DOMContentLoaded", async () => {
        const accountMessage = document.getElementById("account-message");
        try {
            const user = await request("/api/auth/me");
            const role = String(user.role).toUpperCase();
            if (role !== "CUSTOMER") {
                location.replace(role === "ADMIN" ? "/owner/dashboard"
                    : role === "STAFF" ? "/staff/dashboard" : "/customer/login");
                return;
            }
            const profile = await request("/api/customer/profile");
            document.getElementById("profile-email").value = profile.email || "";
            document.getElementById("profile-name").value = profile.full_name || "";
            document.getElementById("profile-phone").value = profile.phone || "";
            await loadSupport();
        } catch (error) {
            show(accountMessage, `Could not load your account: ${error.message}`, true);
        }

        document.getElementById("profile-form").addEventListener("submit", async event => {
            event.preventDefault();
            const result = document.getElementById("profile-result");
            try {
                const profile = await request("/api/customer/profile", {
                    method: "PATCH",
                    body: JSON.stringify({
                        fullName: document.getElementById("profile-name").value,
                        phone: document.getElementById("profile-phone").value
                    })
                });
                document.getElementById("profile-name").value = profile.full_name || "";
                document.getElementById("profile-phone").value = profile.phone || "";
                show(result, "Profile updated.");
            } catch (error) {
                show(result, error.message, true);
            }
        });

        document.getElementById("support-form").addEventListener("submit", async event => {
            event.preventDefault();
            const result = document.getElementById("support-result");
            try {
                const ticket = await request("/api/customer/support", {
                    method: "POST",
                    body: JSON.stringify({
                        subject: document.getElementById("support-subject").value,
                        message: document.getElementById("support-message").value
                    })
                });
                show(result, `Request #${ticket.ticketId} submitted.`);
                event.currentTarget.reset();
                await loadSupport();
            } catch (error) {
                show(result, error.message, true);
            }
        });

        document.querySelector("[data-logout]")?.addEventListener("click", async event => {
            const button = event.currentTarget;
            button.disabled = true;
            try {
                await request("/api/auth/logout", { method: "POST" });
                location.href = "/customer/login";
            } catch (error) {
                button.disabled = false;
                show(accountMessage, `Could not log out: ${error.message}`, true);
            }
        });
    });
})();

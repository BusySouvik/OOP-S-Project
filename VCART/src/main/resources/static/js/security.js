(() => {
    const unsafeMethods = new Set(["POST", "PUT", "PATCH", "DELETE"]);

    function csrfCookie() {
        const cookie = document.cookie
            .split("; ")
            .find(value => value.startsWith("XSRF-TOKEN="));
        return cookie ? decodeURIComponent(cookie.substring("XSRF-TOKEN=".length)) : null;
    }

    async function csrfHeader() {
        let token = csrfCookie();
        if (!token) {
            const response = await fetch("/api/auth/csrf", {
                credentials: "same-origin"
            });
            if (!response.ok) {
                throw new Error(`Could not initialize request security (${response.status}).`);
            }
            token = csrfCookie();
        }
        if (!token) {
            throw new Error("Could not initialize request security.");
        }
        return { "X-XSRF-TOKEN": token };
    }

    window.vcartFetch = async (url, options = {}) => {
        const method = (options.method || "GET").toUpperCase();
        const headers = { ...(options.headers || {}) };
        if (unsafeMethods.has(method)) {
            Object.assign(headers, await csrfHeader());
        }
        return fetch(url, {
            credentials: "same-origin",
            ...options,
            headers
        });
    };
})();

let adminUserId = null;
const $ = id => document.getElementById(id);
const api = async (url, options={}) => {
    const response = await window.vcartFetch(url,{...options,
        headers:{"Content-Type":"application/json",...(options.headers||{})}});
    const text = await response.text();
    let data = {};
    try { data = text ? JSON.parse(text) : {}; } catch {}
    if (!response.ok) throw new Error(data.message || data.error || text || "Request failed");
    return data;
};
const base = () => `/api/admin/${adminUserId}/workspace`;
const msg = text => { $("workspace-message").textContent=text; };
const money = n => "₹"+Number(n||0).toLocaleString("en-IN",{maximumFractionDigits:2});
const esc = value => String(value ?? "").replace(/[&<>"']/g, ch => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch]));
const rows = (id, data, render) => {
    const el=$(id); el.innerHTML="";
    if(!data || !data.length){el.innerHTML='<tr><td colspan="8">No records found.</td></tr>';return;}
    data.forEach(x=>el.insertAdjacentHTML("beforeend",render(x)));
};
const run = async fn => { try { await fn(); } catch(e){msg(e.message);} };

async function loadProducts(keyword="") {
    const data=await api(keyword?`/api/products/search?keyword=${encodeURIComponent(keyword)}`:"/api/products");
    rows("products-table",data,p=>`<tr><td>${p.id}</td><td>${esc(p.name)}</td><td>${esc(p.sku)}</td><td>${money(p.price)}</td><td>${p.stockQuantity}</td><td><button class="small-action" onclick="editProduct(${p.id})">Edit</button> <button class="small-action" onclick="deactivateProduct(${p.id})">Deactivate</button></td></tr>`);
}
async function loadOrders(){
    const data=await api(`/api/admin/${adminUserId}/orders`);
    rows("orders-table",data,order=>`<tr>
        <td>${esc(order.order_number)}</td>
        <td>${esc(order.full_name)}</td>
        <td>${money(order.total_amount)}</td>
        <td>${esc(order.payment_status)}${order.provider==="MOCK"?" · MOCK, NO MONEY MOVED":order.provider==="LEGACY"?" · LEGACY, UNVERIFIED":order.provider?` · ${esc(order.provider)}`:""}</td>
        <td>${esc(order.status)}</td>
        <td><select data-order-status="${esc(order.id)}">
            ${["PLACED","PROCESSING","SHIPPED","DELIVERED","CANCELLED"].map(status=>`<option value="${status}" ${status===order.status?"selected":""}>${status}</option>`).join("")}
        </select> <button class="small-action" data-save-order="${esc(order.id)}">Save</button></td>
    </tr>`);
}
async function editProduct(id){
    await run(async()=>{
        const current=await api(`/api/products/${id}`);
        const newName=prompt("Product name",current.name); if(newName===null)return;
        const newPrice=prompt("Price",current.price); if(newPrice===null)return;
        const newStock=prompt("Stock quantity",current.stockQuantity); if(newStock===null)return;
        const payload={...current,name:newName.trim(),price:Number(newPrice),stockQuantity:Number(newStock)};
        await api(`/api/products/${id}`,{method:"PUT",body:JSON.stringify(payload)});
        msg("Product updated.");await loadProducts();
    });
}
async function deactivateProduct(id){
    if(!confirm("Deactivate this product?"))return;
    await run(async()=>{await api(`/api/products/${id}`,{method:"DELETE"});msg("Product deactivated.");await loadProducts();});
}
async function loadCategories(){
    const data=await api(base()+"/categories");
    rows("categories-table",data,c=>`<tr><td>${c.id}</td><td>${esc(c.name)}</td><td>${esc(c.description||"—")}</td><td>${c.is_active?"Active":"Inactive"}</td><td><button class="small-action" onclick="editCategory(${c.id})">Edit</button> <button class="small-action" onclick="disableCategory(${c.id})">Deactivate</button></td></tr>`);
}
async function editCategory(id){
    await run(async()=>{
        const categories=await api(base()+"/categories");
        const category=categories.find(item=>Number(item.id)===Number(id));
        if(!category)throw new Error("Category not found.");
        const n=prompt("Category name",category.name);if(n===null)return;
        const d=prompt("Description",category.description||"");if(d===null)return;
        await api(base()+`/categories/${id}`,{method:"PUT",body:JSON.stringify({name:n,description:d})});
        await loadCategories();msg("Category updated.");
    });
}
async function disableCategory(id){
    if(!confirm("Deactivate category?"))return;
    await run(async()=>{await api(base()+`/categories/${id}`,{method:"DELETE"});await loadCategories();msg("Category deactivated.");});
}
async function loadInventory(){
    const data=await api(base()+"/inventory/alerts");
    rows("inventory-table",data,p=>`<tr><td>${esc(p.name)}</td><td>${esc(p.sku)}</td><td>${esc(p.stock_quantity)}</td><td><input type="number" min="0" id="stock-${p.id}" value="${esc(p.stock_quantity)}" style="width:85px"><button class="small-action" onclick="saveStock(${p.id})">Save</button></td></tr>`);
}
async function saveStock(id){
    await run(async()=>{await api(base()+`/inventory/${id}`,{method:"PATCH",body:JSON.stringify({quantity:Number($("stock-"+id).value)})});await loadInventory();msg("Stock updated.");});
}
async function loadCustomers(){
    const data=await api(base()+"/customers");
    rows("customers-table",data,c=>`<tr><td>${esc(c.full_name)}</td><td>${esc(c.email)}</td><td>${esc(c.phone||"—")}</td><td>${c.is_active?"Active":"Disabled"}</td><td><button class="small-action" onclick="toggleCustomer(${c.id},${!c.is_active})">${c.is_active?"Disable":"Enable"}</button></td></tr>`);
}
async function toggleCustomer(id,active){
    if(!confirm("Change this customer account status?"))return;
    await run(async()=>{await api(base()+`/customers/${id}`,{method:"PATCH",body:JSON.stringify({active})});await loadCustomers();msg("Customer updated.");});
}
async function loadTransactions(){
    const data=await api(base()+"/transactions");
    rows("transactions-table",data,t=>`<tr><td>${esc(t.order_number)}</td><td>${esc(t.user_id)}</td><td>${money(t.total_amount)}</td><td>${esc(t.payment_status)}</td><td>${esc(t.provider||"—")}</td><td>${esc(t.payment_attempt_status||"—")}</td><td>${esc(t.status)}</td><td>${esc(t.created_at||"—")}</td></tr>`);
}
async function loadRefunds(){
    const data=await api(base()+"/refunds");
    rows("refunds-table",data,r=>{
        const action=r.status==="REQUESTED"
            ? `<select id="refund-${r.id}"><option value="APPROVED">Approve</option><option value="REJECTED">Reject</option></select><button class="small-action" onclick="saveRefund(${r.id})">Review</button>`
            : r.status==="APPROVED" && r.payment_provider==="MOCK"
                ? `<button class="small-action" onclick="processRefund(${r.id})">Process mock refund</button>`
                : r.status==="PROCESSING" ? "Awaiting provider completion."
                : r.status==="APPROVED" ? "No compatible refund provider is configured."
                : "—";
        const providerLabel=r.payment_provider==="MOCK"?"MOCK · NO MONEY MOVED":r.payment_provider||"UNVERIFIED";
        return `<tr><td>${esc(r.order_number)}</td><td>${money(r.total_amount)} ${esc(r.currency||"INR")}</td><td>${esc(providerLabel)}</td><td>${esc(r.reason)}</td><td>${esc(r.status)}${r.status==="PROCESSED"&&r.payment_provider==="MOCK"?" · SIMULATED":""}</td><td>${action}</td></tr>`;
    });
}
async function saveRefund(id){
    const note=prompt("Optional review note","");if(note===null)return;
    await run(async()=>{await api(base()+`/refunds/${id}`,{method:"PATCH",body:JSON.stringify({status:$("refund-"+id).value,adminNote:note})});await loadRefunds();msg("Refund review saved.");});
}
async function processRefund(id){
    if(!confirm("Process this using the development mock? No real funds will move."))return;
    await run(async()=>{const result=await api(base()+`/refunds/${id}/process`,{method:"POST"});await loadRefunds();msg(result.message||"Mock refund status updated; no real funds moved.");});
}
async function loadStaff(){
    const data=await api(base()+"/staff");
    rows("staff-table",data,s=>{
        const login=s.user_id?(s.account_active?"Enabled":"Disabled"):"No login";
        const loginAction=s.user_id
            ? `<button class="small-action" onclick="resetStaffPassword(${s.id})">Reset password</button>`
            : `<button class="small-action" onclick="enableStaffAccount(${s.id})">Create login</button>`;
        return `<tr><td>${esc(s.full_name)}</td><td>${esc(s.email)}</td><td>${esc(s.job_title)}</td><td>${s.is_active?"Active":"Inactive"}</td><td>${login}</td><td><button class="small-action" onclick="editStaff(${s.id})">Edit</button> <button class="small-action" onclick="toggleStaff(${s.id},${!s.is_active})">${s.is_active?"Disable":"Enable"}</button> ${loginAction}</td></tr>`;
    });
}
async function toggleStaff(id,active){
    await run(async()=>{await api(base()+`/staff/${id}`,{method:"PATCH",body:JSON.stringify({active})});await loadStaff();msg("Staff record updated.");});
}
async function editStaff(id){
    await run(async()=>{
        const staff=(await api(base()+"/staff")).find(item=>Number(item.id)===Number(id));
        if(!staff)throw new Error("Staff record not found.");
        const fullName=prompt("Staff name",staff.full_name);if(fullName===null)return;
        const email=prompt("Staff email",staff.email);if(email===null)return;
        const jobTitle=prompt("Job title",staff.job_title);if(jobTitle===null)return;
        await api(base()+`/staff/${id}`,{method:"PUT",body:JSON.stringify({fullName,email,jobTitle})});
        await loadStaff();msg("Staff details updated.");
    });
}
async function enableStaffAccount(id){
    const password=prompt("Set an initial password (8-72 UTF-8 bytes)");
    if(password===null)return;
    await run(async()=>{await api(base()+`/staff/${id}/account`,{method:"POST",body:JSON.stringify({password})});await loadStaff();msg("Staff login created.");});
}
async function resetStaffPassword(id){
    const password=prompt("Set a new staff password (8-72 UTF-8 bytes)");
    if(password===null)return;
    await run(async()=>{await api(base()+`/staff/${id}/password`,{method:"POST",body:JSON.stringify({password})});msg("Staff password reset.");});
}
async function loadSupport(){
    const data=await api(base()+"/support");
    rows("support-table",data,s=>`<tr><td>${esc(s.subject)}</td><td>${esc(s.message)}</td><td>${esc(s.email||"—")}</td><td>${s.status}</td><td><select id="support-${s.id}"><option ${s.status==="OPEN"?"selected":""}>OPEN</option><option ${s.status==="IN_PROGRESS"?"selected":""}>IN_PROGRESS</option><option ${s.status==="RESOLVED"?"selected":""}>RESOLVED</option></select><button class="small-action" onclick="saveSupport(${s.id})">Save</button></td></tr>`);
}
async function saveSupport(id){
    await run(async()=>{await api(base()+`/support/${id}`,{method:"PATCH",body:JSON.stringify({status:$("support-"+id).value})});await loadSupport();msg("Support status updated.");});
}
async function loadFinance(){
    const f=await api(base()+"/finance");
    $("fin-orders").textContent=f.order_count;
    $("fin-gross").textContent=money(f.gross_order_value);
    $("fin-paid").textContent=money(f.verified_revenue);
    $("fin-mock").textContent=money(f.simulated_paid_amount);
    $("fin-pending").textContent=f.pending_payments;
    $("fin-failed").textContent=f.failed_payments;
}
async function loadCoupons(){
    const data=await api("/api/discounts");
    rows("coupons-table",data,c=>`<tr><td>${esc(c.code)}</td><td>${esc(c.type)}</td><td>${c.value}</td><td>${c.used_count??0}</td><td>${c.is_active?"Yes":"No"}</td><td><button class="small-action" onclick="disableCoupon(${JSON.stringify(c.code)})">Deactivate</button></td></tr>`);
}
async function disableCoupon(code){
    if(!confirm("Deactivate coupon "+code+"?"))return;
    await run(async()=>{await api("/api/discounts/"+encodeURIComponent(code)+"/deactivate",{method:"PATCH"});await loadCoupons();msg("Coupon deactivated.");});
}
const loaders={orders:loadOrders,products:()=>loadProducts(),categories:loadCategories,inventory:loadInventory,customers:loadCustomers,transactions:loadTransactions,refunds:loadRefunds,staff:loadStaff,support:loadSupport,finance:loadFinance,coupons:loadCoupons};
function showSection(name){
    document.querySelectorAll(".workspace-section").forEach(s=>s.hidden=s.id!=="section-"+name);
    document.querySelectorAll("#workspace-nav .nav-link").forEach(a=>a.classList.toggle("active",a.dataset.section===name));
    $("workspace-title").textContent=({orders:"Order Management",products:"Product Management",categories:"Categories & Attributes",inventory:"Inventory Management",customers:"Customer Management",transactions:"Transactions",refunds:"Refund Management",staff:"Staff Directory",support:"Support Inbox",finance:"Finance Overview",coupons:"Coupons & Discounts"})[name]||"Management";
    if(loaders[name])run(loaders[name]);
}
document.addEventListener("DOMContentLoaded",async()=>{
    if(document.body.dataset.page!=="workspace")return;
    try{
        const me=await api("/api/auth/me");
        if(me.role!=="ADMIN"){location.href="/";return;}
        adminUserId=me.id;
        document.querySelector("[data-logout]")?.addEventListener("click",async()=>{
            await run(async()=>{await api("/api/auth/logout",{method:"POST"});location.href="/owner/login";});
        });
        document.querySelectorAll("#workspace-nav a[data-section]").forEach(a=>a.addEventListener("click",()=>showSection(a.dataset.section)));
        $("orders-table").addEventListener("click",event=>{
            const button=event.target.closest("[data-save-order]");
            if(!button)return;
            run(async()=>{
                const id=button.dataset.saveOrder;
                const status=button.parentElement.querySelector("select").value;
                await api(`/api/admin/${adminUserId}/orders/${id}/status`,{method:"PATCH",body:JSON.stringify({status})});
                msg("Order status updated.");await loadOrders();
            });
        });
        $("add-product").onclick=()=>run(async()=>{
            const product={name:$("p-name").value,sku:$("p-sku").value,slug:$("p-slug").value||$("p-name").value.toLowerCase().trim().replace(/[^a-z0-9]+/g,"-"),price:Number($("p-price").value),stockQuantity:Number($("p-stock").value),categoryId:$("p-category").value?Number($("p-category").value):null,imageUrl:$("p-image").value,description:$("p-description").value};
            await api("/api/products",{method:"POST",body:JSON.stringify(product)});msg("Product created.");await loadProducts();
        });
        $("search-products").onclick=()=>run(()=>loadProducts($("product-search").value));
        $("load-products").onclick=()=>run(()=>loadProducts());
        $("add-category").onclick=()=>run(async()=>{await api(base()+"/categories",{method:"POST",body:JSON.stringify({name:$("cat-name").value,description:$("cat-description").value})});$("cat-name").value="";$("cat-description").value="";await loadCategories();msg("Category created.");});
        $("add-staff").onclick=()=>run(async()=>{
            await api(base()+"/staff",{method:"POST",body:JSON.stringify({fullName:$("staff-name").value,email:$("staff-email").value,jobTitle:$("staff-title").value,password:$("staff-password").value})});
            $("staff-password").value="";await loadStaff();msg("Staff login created.");
        });
        $("create-support").onclick=()=>run(async()=>{await api(base()+"/support",{method:"POST",body:JSON.stringify({subject:$("support-subject").value,message:$("support-message").value})});await loadSupport();msg("Support message created.");});
        $("create-coupon").onclick=()=>run(async()=>{const data={code:$("coupon-code").value,type:$("coupon-type").value,value:$("coupon-value").value,minimumOrderAmount:$("coupon-minimum").value||0,maximumDiscount:$("coupon-maximum").value||null,usageLimit:$("coupon-limit").value||null};await api("/api/discounts",{method:"POST",body:JSON.stringify(data)});await loadCoupons();msg("Coupon created.");});
        showSection(location.hash.substring(1)||"products");
    }catch(e){msg(e.message);}
});
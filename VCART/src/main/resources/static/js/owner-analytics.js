(() => {
  const $ = id => document.getElementById(id);
  const money = value => new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:2}).format(Number(value||0));
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  async function request(url, options={}) {
    const response = await window.vcartFetch(url,{...options,headers:{'Content-Type':'application/json',...(options.headers||{})}});
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(data.message || `Request failed (${response.status}).`);
      error.status = response.status;
      throw error;
    }
    return data;
  }
  const profileWrap = $('profile-wrap');
  const profileToggle = $('profile-toggle');
  const profileMenu = $('profile-menu');
  function setProfileOpen(open) {
    profileMenu.hidden = !open;
    profileToggle.setAttribute('aria-expanded', String(open));
  }
  profileToggle.addEventListener('click', () => setProfileOpen(profileMenu.hidden));
  document.addEventListener('click', event => {
    if (!profileWrap.contains(event.target)) setProfileOpen(false);
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !profileMenu.hidden) {
      setProfileOpen(false);
      profileToggle.focus();
    }
  });
  async function load() {
    $('error').hidden=true;
    try {
      const me = await request('/api/auth/me');
      if (!me || String(me.role).toUpperCase()!=='ADMIN') {
        window.location.replace('/owner/login');
        return;
      }
      $('profile-name').textContent = me.full_name || 'Not provided';
      $('profile-email').textContent = me.email || 'Not provided';
      $('profile-role').textContent = me.role || 'Not provided';
      $('welcome-message').textContent = me.full_name ? `Welcome back, ${me.full_name}.` : 'Welcome to your store dashboard.';
      const base=`/api/admin/${encodeURIComponent(me.id)}/analytics`;
      const [o,sales,products,orders]=await Promise.all([request(base+'/overview'),request(base+'/sales'),request(base+'/top-products'),request(base+'/recent-orders')]);
      $('revenue').textContent=money(o.revenue); $('todayRevenue').textContent=money(o.today_revenue);
      $('todayOrders').textContent=`${o.today_orders||0} orders today`; $('orders').textContent=o.orders||0;
      $('buyers').textContent=o.buying_customers||0; $('customers').textContent=`${o.customers||0} registered customers`;
      $('products').textContent=o.active_products||0; $('units').textContent=`${o.units_in_stock||0} units in stock`;
      $('lowStock').textContent=o.low_stock||0;
      const max=Math.max(1,...sales.map(x=>Number(x.revenue||0)));
      $('salesChart').innerHTML=sales.map(x=>`<div class="bar-item"><strong>${money(x.revenue)}</strong><div class="bar-track"><div class="bar-fill" style="height:${Math.max(3,Number(x.revenue||0)/max*100)}%"></div></div><span>${esc(x.day)}</span><small>${x.orders} orders</small></div>`).join('');
      $('topProducts').innerHTML=products.length?products.map(x=>`<tr><td>${esc(x.name)}</td><td>${x.units_sold}</td><td>${money(x.sales)}</td></tr>`).join(''):'<tr><td colspan="3">No sales recorded yet.</td></tr>';
      $('recentOrders').innerHTML=orders.length?orders.map(x=>`<tr><td>${esc(x.order_number)}</td><td>${esc(x.customer)}</td><td>${money(x.total_amount)}</td><td>${esc(x.payment_status)}${x.provider==='MOCK'?' · MOCK, NO MONEY MOVED':x.provider==='LEGACY'?' · LEGACY, UNVERIFIED':x.provider?` · ${esc(x.provider)}`:''}</td><td><span class="status">${esc(x.status)}</span></td></tr>`).join(''):'<tr><td colspan="5">No orders yet.</td></tr>';
    } catch(error) {
      if (error.status === 401 || error.status === 403) {
        window.location.replace('/owner/login');
        return;
      }
      $('error').textContent=error.message;
      $('error').hidden=false;
    }
  }
  $('refresh').addEventListener('click',load);
  $('logout').addEventListener('click',async()=>{
    const button=$('logout');
    const error=$('logout-error');
    button.disabled=true;
    button.textContent='Signing out…';
    error.hidden=true;
    try {
      await request('/api/auth/logout',{method:'POST'});
      setProfileOpen(false);
      window.location.replace('/owner/login');
    } catch (logoutError) {
      error.textContent=logoutError.message || 'Unable to log out. Please try again.';
      error.hidden=false;
      button.disabled=false;
      button.textContent='Log Out';
      setProfileOpen(true);
    }
  });
  load();
})();

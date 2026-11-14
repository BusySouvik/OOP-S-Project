package com.vcart.controller;

import com.vcart.service.OwnerReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.security.core.Authentication;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Controller
public class OwnerReportController {
    private final OwnerReportService reports;
    public OwnerReportController(OwnerReportService reports) { this.reports = reports; }

    @GetMapping("/admin/reports")
    public String page() { return "owner-reports"; }

    @GetMapping(value="/api/admin/reports/orders.csv", produces="text/csv")
    @ResponseBody
    public ResponseEntity<byte[]> orders(Authentication auth) {
        return csv("vcart-orders.csv", rows(reports.orders(auth.getName()),
            "Order Number","Customer","Email","Subtotal","Discount","Total","Order Status","Payment Status","Payment Attempt","Payment Provider","Payment Classification","Shipping Address","Created At",
            "order_number","full_name","email","subtotal","discount_amount","total_amount","status","payment_status","payment_attempt_status","payment_provider","payment_classification","shipping_address","created_at"));
    }

    @GetMapping(value="/api/admin/reports/products.csv", produces="text/csv")
    @ResponseBody
    public ResponseEntity<byte[]> products(Authentication auth) {
        return csv("vcart-products.csv", rows(reports.products(auth.getName()),
            "SKU","Product","Category","Price","Stock","Active","Units Sold",
            "sku","name","category","price","stock_quantity","is_active","units_sold"));
    }

    private String rows(List<Map<String,Object>> data, String... fields) {
        StringBuilder out = new StringBuilder();
        for (int i=0;i<fields.length;i++) { if(i>0) out.append(','); out.append(quote(fields[i])); }
        out.append("\r\n");
        int n=fields.length/2;
        for (Map<String,Object> row:data) {
            for(int i=0;i<n;i++) {
                if(i>0) out.append(',');
                Object value=row.get(fields[n+i]);
                out.append(quote(value == null ? "" : value.toString()));
            }
            out.append("\r\n");
        }
        return out.toString();
    }
    private String quote(String value) {
        String safe=value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe="'"+safe;
        return "\""+safe.replace("\"","\"\"")+"\"";
    }
    private ResponseEntity<byte[]> csv(String filename, String content) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+filename+"\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(("\uFEFF"+content).getBytes(StandardCharsets.UTF_8));
    }
}

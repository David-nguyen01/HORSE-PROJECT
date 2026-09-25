package com.racehorse.admin;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Map;
@RestController @RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_REPORTING_VIEW')")
public class ManagementDashboardController {
    private final ManagementDashboardService service;
    public ManagementDashboardController(ManagementDashboardService service){this.service=service;}
    @GetMapping public Map<String,Object> get(@RequestParam String month,@RequestParam(required=false) String compareTo){return service.dashboard(month,compareTo);}
}

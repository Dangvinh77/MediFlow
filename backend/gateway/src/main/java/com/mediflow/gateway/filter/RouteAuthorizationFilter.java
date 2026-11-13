package com.mediflow.gateway.filter;

import com.mediflow.common.security.Roles;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/** Default-deny route/method/role matrix at the external Gateway boundary. */
@Component
public class RouteAuthorizationFilter implements GlobalFilter, Ordered {

    public static final String ROLE_ATTRIBUTE = RouteAuthorizationFilter.class.getName() + ".role";
    private static final PathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<RouteRule> RULES = List.of(
            // Transport roles only: Core/Clinical recheck the live appointment and department.
            rule("/api/v1/org/me/capabilities", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.PHARMACIST, Roles.CASHIER, Roles.LAB_TECH),
            rule("/api/v2/org/departments/{id}/head-appointments", HttpMethod.GET, Roles.ADMIN),
            rule("/api/v2/org/departments/{id}/head-appointments", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v2/org/staff/{id}/capability-grants", HttpMethod.GET, Roles.ADMIN),
            rule("/api/v2/org/staff/{id}/capability-grants", HttpMethod.POST, Roles.ADMIN),
            rule("/api/v2/org/capability-grants/{id}/revocation", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v2/doctor-work-shifts", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v2/doctor-work-shifts", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v2/doctor-work-shifts/mine", HttpMethod.GET, Roles.DOCTOR),
            rule("/api/v2/doctor-work-shifts/{id}", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v2/doctor-work-shifts/{id}/cancel", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v2/doctor-work-shifts/{id}/consultation-sessions", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/org/care-labels/{kind}/{id}", HttpMethod.GET,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE,
                    Roles.PHARMACIST, Roles.CASHIER, Roles.LAB_TECH, Roles.PATIENT),
            rule("/api/v2/org/consultation-rooms", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v2/consultation-sessions", HttpMethod.GET, Roles.ADMIN, Roles.NURSE, Roles.DOCTOR),
            rule("/api/v2/consultation-sessions", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v2/consultation-sessions/mine", HttpMethod.GET, Roles.DOCTOR),
            rule("/api/v2/consultation-sessions/summary", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v2/consultation-sessions/{id}", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v2/consultation-sessions/{id}/status", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v2/consultation-sessions/{id}/registrations", HttpMethod.GET,
                    Roles.ADMIN, Roles.NURSE, Roles.DOCTOR),
            rule("/api/v2/outpatient-registrations", HttpMethod.POST, Roles.ADMIN, Roles.NURSE),
            rule("/api/v2/outpatient-registrations/mine", HttpMethod.GET, Roles.PATIENT),
            rule("/api/v2/outpatient-registrations/follow-up-sources", HttpMethod.GET, Roles.ADMIN, Roles.NURSE),
            rule("/api/v2/records/access-declarations", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v2/records/{id}/capabilities", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v2/records/{id}/follow-up-recommendations", HttpMethod.POST, Roles.DOCTOR),
            rule("/api/v2/records/{id}/follow-up-recommendation", HttpMethod.GET,
                    Roles.ADMIN, Roles.NURSE, Roles.DOCTOR, Roles.PATIENT),
            rule("/api/v2/outpatient-registrations/{id}", HttpMethod.GET,
                    Roles.ADMIN, Roles.NURSE, Roles.DOCTOR, Roles.PATIENT),
            rule("/api/v2/outpatient-registrations/{id}/arrival", HttpMethod.PUT, Roles.ADMIN, Roles.NURSE),
            rule("/api/v2/outpatient-registrations/{id}/cancellation", HttpMethod.PUT,
                    Roles.ADMIN, Roles.NURSE, Roles.DOCTOR),
            rule("/api/v2/billing/payment-requests", HttpMethod.GET, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v2/billing/payment-requests/by-registration", HttpMethod.GET, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v2/billing/payment-requests/{id}/payments", HttpMethod.POST, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v2/billing/payment-requests/{id}/quote", HttpMethod.GET, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v2/billing/payment-requests/{id}/zero-payable-eligibility", HttpMethod.POST, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v2/billing/payment-requests/{id}/lab-zero-payable-eligibility", HttpMethod.POST, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v2/billing/payment-operations/{id}", HttpMethod.GET, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v2/billing/registration-settlements", HttpMethod.GET, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v2/outpatient-registrations/{id}/room-queue-ticket", HttpMethod.POST, Roles.NURSE),
            rule("/api/v2/clinic-queue", HttpMethod.GET, Roles.NURSE, Roles.DOCTOR),
            rule("/api/v2/clinic-queue-tickets/{id}/call", HttpMethod.PUT, Roles.NURSE, Roles.DOCTOR),
            rule("/api/v2/clinic-queue-tickets/{id}/adjustments", HttpMethod.POST, Roles.NURSE, Roles.DOCTOR),
            rule("/api/v2/outpatient-registrations/{id}/vital-signs", HttpMethod.POST, Roles.NURSE),
            rule("/api/v2/outpatient-registrations/{id}/vital-signs", HttpMethod.GET, Roles.NURSE, Roles.DOCTOR),
            rule("/api/v2/records/{id}/close-without-conclusion", HttpMethod.PUT, Roles.DOCTOR),
            rule("/api/v2/records/{id}/lab-orders/{orderId}/withdrawal", HttpMethod.PUT, Roles.DOCTOR),
            rule("/api/v2/patients/{id}/open-visits", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v2/records/{id}/lab-orders", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v2/doctors/me/open-visits", HttpMethod.GET, Roles.DOCTOR),
            rule("/api/v2/patients/{id}/return-sources", HttpMethod.GET, Roles.ADMIN, Roles.NURSE),
            rule("/api/v2/patients/{id}/timeline", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v2/records/{id}/documents", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v2/treatment-courses", HttpMethod.POST, Roles.DOCTOR),
            rule("/api/v2/treatment-courses/{id}/visits", HttpMethod.POST, Roles.DOCTOR),
            rule("/api/v2/treatment-courses/{id}/closure", HttpMethod.PUT, Roles.DOCTOR),
            rule("/api/v2/patients/{id}/treatment-courses", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v2/records/{id}/handovers", HttpMethod.POST, Roles.DOCTOR),
            rule("/api/v2/records/{id}/handovers", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v2/records/{id}/handovers/{handoverId}/acceptance", HttpMethod.PUT, Roles.DOCTOR),
            rule("/api/v2/records/{id}/consultation-notes", HttpMethod.POST, Roles.DOCTOR),
            rule("/api/v2/records/{id}/consultation-notes", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v2/records/{id}/amendments", HttpMethod.POST, Roles.DOCTOR),
            rule("/api/v2/records/{id}/amendments", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v2/records/{id}/amendments/{amendmentId}/decision", HttpMethod.PUT, Roles.DOCTOR),
            rule("/api/v2/records/{id}/amendment-assignments", HttpMethod.POST, Roles.DOCTOR),
            rule("/api/v2/records/{id}/amendment-assignments/{assignmentId}", HttpMethod.DELETE, Roles.DOCTOR),
            rule("/api/v2/amendments/pending", HttpMethod.GET, Roles.DOCTOR),
            rule("/api/v2/patients/{id}/allergies", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v2/patients/{id}/allergies", HttpMethod.POST, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v2/patients/{id}/allergies/{allergyId}/status", HttpMethod.PUT, Roles.DOCTOR),
            rule("/api/v2/outpatient-registrations/{id}/reschedule", HttpMethod.POST, Roles.ADMIN, Roles.NURSE),
            rule("/api/v2/consultation-sessions/{id}/impact", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v2/doctor-work-shifts/{id}/impact", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/org/operating-rooms", HttpMethod.POST, Roles.ADMIN),
            rule("/api/v1/org/operating-rooms/{id}", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v1/org/departments", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/org/departments/{id}", HttpMethod.GET,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/org/departments", HttpMethod.POST, Roles.ADMIN),
            rule("/api/v1/org/rooms", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/org/rooms/{id}", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/org/rooms", HttpMethod.POST, Roles.ADMIN),
            rule("/api/v1/org/rooms/{id}", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v1/org/rooms/{id}/status", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v1/org/room-types", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/org/room-types", HttpMethod.POST, Roles.ADMIN),
            rule("/api/v1/org/room-types/{code}", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v1/org/room-types/{code}", HttpMethod.DELETE, Roles.ADMIN),
            rule("/api/v1/org/departments/{id}", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v1/org/staff", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/org/staff/{id}", HttpMethod.GET,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE,
                    Roles.PHARMACIST, Roles.CASHIER, Roles.LAB_TECH),
            rule("/api/v1/org/staff", HttpMethod.POST, Roles.ADMIN),
            rule("/api/v1/org/staff/{id}", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v1/org/staff/{id}/deactivate", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v1/org/staff/{id}/department", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v1/org/staff/{id}/contact", HttpMethod.PUT,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE,
                    Roles.PHARMACIST, Roles.CASHIER, Roles.LAB_TECH),
            rule("/api/v1/org/staff/{id}/department", HttpMethod.GET,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE,
                    Roles.PHARMACIST, Roles.CASHIER, Roles.LAB_TECH),
            rule("/api/v1/org/accounts", HttpMethod.GET, Roles.ADMIN),
            rule("/api/v1/org/accounts/{id}", HttpMethod.GET, Roles.ADMIN),
            rule("/api/v1/org/accounts/me/password", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.PHARMACIST, Roles.CASHIER, Roles.LAB_TECH, Roles.PATIENT),
            rule("/api/v1/org/accounts/{id}/password", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v1/org/accounts/{id}/email", HttpMethod.PUT, Roles.ADMIN),
            rule("/api/v1/org/accounts/{id}/mail-delivery", HttpMethod.GET, Roles.ADMIN),
            rule("/api/v1/org/accounts/{id}/mail-delivery/{messageId}/retry", HttpMethod.POST, Roles.ADMIN),
            rule("/api/v1/org/patient-accounts", HttpMethod.POST, Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/org/patient-accounts/{patientId}/email", HttpMethod.PUT, Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/org/profile-accounts/staff/{staffId}", HttpMethod.GET, Roles.ADMIN),
            rule("/api/v1/org/profile-accounts/patient/{patientId}", HttpMethod.GET, Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/org/patient-accounts/{patientId}/password", HttpMethod.PUT, Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/org/patient-accounts/{patientId}/mail-delivery", HttpMethod.GET, Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/org/patient-accounts/{patientId}/mail-delivery/{messageId}/retry", HttpMethod.POST,
                    Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/org/accounts", HttpMethod.POST, Roles.ADMIN),
            rule("/api/v1/org/accounts/{id}/status", HttpMethod.PUT, Roles.ADMIN),

            rule("/api/v1/patients", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/patients/directory/search", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.PHARMACIST, Roles.CASHIER, Roles.LAB_TECH),
            rule("/api/v1/patients/directory/{id}", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.PHARMACIST, Roles.CASHIER, Roles.LAB_TECH),
            rule("/api/v1/patients/{id}", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.PATIENT),
            rule("/api/v1/patients", HttpMethod.POST, Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/patients/{id}", HttpMethod.PUT, Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/patients/{id}", HttpMethod.DELETE, Roles.ADMIN),

            rule("/api/v1/appointments", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/appointments/**", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/appointments", HttpMethod.POST, Roles.ADMIN, Roles.NURSE),
            // Commands are enumerated by their exact URI shape. A broad ** command rule
            // would union roles and authorize nested/future endpoints accidentally.
            rule("/api/v1/appointments/{id}", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/appointments/{id}/status", HttpMethod.PUT,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/appointments/{id}/check-in", HttpMethod.PUT,
                    Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/appointments/{id}/start-exam", HttpMethod.PUT,
                    Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/records/mine", HttpMethod.GET, Roles.PATIENT),
            rule("/api/v1/records/{id:[0-9a-fA-F-]+}", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/records/patient/{patientId}", HttpMethod.GET,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/records", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/records/{id}/lab-orders", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/records/{id}/admission-referrals", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/records/{id}", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/records/{id}/complete", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/records/{id}/diagnoses", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR),

            // Inpatient routes mirror the downstream controller's method-level guards.
            rule("/api/v1/inpatient/admissions", HttpMethod.GET,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.CASHIER),
            // Keep this to the controller's single-resource endpoint. A broad /** GET rule
            // would also authorize unsupported action paths such as treatment corrections.
            rule("/api/v1/inpatient/admissions/*", HttpMethod.GET,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.CASHIER),
            rule("/api/v1/inpatient/beds", HttpMethod.GET,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/inpatient/admissions", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/inpatient/admissions/**/admit", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/inpatient/admissions/**/treatments", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/inpatient/admissions/*/treatments/*/corrections", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/inpatient/admissions/**/order-references", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/inpatient/admissions/**/medical-discharge", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/inpatient/admissions/**/close", HttpMethod.POST,
                    Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/inpatient/admissions/**/cancel", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/inpatient/beds", HttpMethod.POST,
                    Roles.ADMIN),
            rule("/api/v1/inpatient/admissions/**/bed", HttpMethod.PUT,
                    Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/inpatient/admissions/**/bed/transfer", HttpMethod.PUT,
                    Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/inpatient/admissions/**/bed/release", HttpMethod.PUT,
                    Roles.ADMIN, Roles.NURSE),
            rule("/api/v1/inpatient/beds/**", HttpMethod.PUT,
                    Roles.ADMIN),

            // Surgery list/detail/schedule and commands mirror downstream @PreAuthorize rules.
            rule("/api/v1/surgery/cases", HttpMethod.GET,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/surgery/cases/{id}", HttpMethod.GET,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE),
            rule("/api/v1/surgery/cases/{id}/schedule", HttpMethod.PUT,
                    Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/surgery/cases/*/preop", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/surgery/cases/*/cancel", HttpMethod.POST,
                    Roles.ADMIN, Roles.DOCTOR),

            rule("/api/v1/lab/mine", HttpMethod.GET, Roles.PATIENT),
            rule("/api/v2/lab/catalog", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.LAB_TECH),
            rule("/api/v2/lab/{id}/template", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.LAB_TECH),
            rule("/api/v2/lab/worklist", HttpMethod.GET, Roles.ADMIN, Roles.LAB_TECH),
            rule("/api/v2/lab/critical-notices", HttpMethod.GET, Roles.DOCTOR, Roles.LAB_TECH),
            rule("/api/v2/lab/{id}/critical-notices/{noticeId}/acknowledgement", HttpMethod.POST, Roles.DOCTOR),
            rule("/api/v2/lab/{id}/report", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.LAB_TECH),
            rule("/api/v2/lab/{id}/report/draft", HttpMethod.PUT, Roles.LAB_TECH),
            rule("/api/v2/lab/{id}/report/submission", HttpMethod.POST, Roles.LAB_TECH),
            rule("/api/v2/lab/{id}/report/return", HttpMethod.POST, Roles.LAB_TECH),
            rule("/api/v2/lab/{id}/report/release", HttpMethod.POST, Roles.LAB_TECH),
            rule("/api/v2/lab/{id}/report/amendments", HttpMethod.POST, Roles.LAB_TECH),
            rule("/api/v2/lab/{id}/specimens", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.LAB_TECH),
            rule("/api/v2/lab/{id}/specimens", HttpMethod.POST, Roles.NURSE, Roles.LAB_TECH),
            rule("/api/v2/lab/specimens/{id}/decision", HttpMethod.PUT, Roles.LAB_TECH),
            rule("/api/v1/lab", HttpMethod.GET,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.LAB_TECH),
            rule("/api/v1/lab/{id:[0-9a-fA-F-]+}", HttpMethod.GET,
                    Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.LAB_TECH),
            rule("/api/v1/lab/patient/**", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/lab", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/lab/{id}/start", HttpMethod.PUT, Roles.ADMIN, Roles.LAB_TECH),
            rule("/api/v1/lab/{id}/cancel", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR, Roles.LAB_TECH),
            rule("/api/v1/lab/{id}/results", HttpMethod.PUT, Roles.ADMIN, Roles.LAB_TECH),
            rule("/api/v1/lab/{id}/status", HttpMethod.PUT, Roles.ADMIN, Roles.LAB_TECH),

            rule("/api/v1/pharmacy/drugs", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.PHARMACIST),
            rule("/api/v1/pharmacy/drugs/{id}", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.PHARMACIST),
            rule("/api/v1/pharmacy/drugs", HttpMethod.POST, Roles.ADMIN, Roles.PHARMACIST),
            rule("/api/v1/pharmacy/drugs/{id}/stock", HttpMethod.PUT, Roles.ADMIN, Roles.PHARMACIST),
            rule("/api/v1/pharmacy/prescriptions", HttpMethod.POST, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/pharmacy/prescriptions", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.PHARMACIST),
            rule("/api/v1/pharmacy/prescriptions/prescribing/context", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/pharmacy/prescriptions/{id}", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR, Roles.PHARMACIST),
            rule("/api/v1/pharmacy/prescriptions/{id}", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/pharmacy/prescriptions/{id}/cancel", HttpMethod.PUT, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/pharmacy/prescriptions/{id}/dispense", HttpMethod.PUT, Roles.ADMIN, Roles.PHARMACIST),
            rule("/api/v1/pharmacy/admin/outbox/{id}/replay", HttpMethod.POST, Roles.ADMIN),

            rule("/api/v2/billing/payment-requests/{id}/lab-payments", HttpMethod.POST, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/invoices/**", HttpMethod.GET, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/patient/**", HttpMethod.GET, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/invoices", HttpMethod.POST, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/payment-requests/by-source", HttpMethod.GET, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/payment-requests/queue", HttpMethod.GET, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/payment-requests/{id}/payments", HttpMethod.POST, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/prescription-adjustments/{id}/refunds-due", HttpMethod.GET,
                    Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/lab-adjustments/{testId}/refunds-due", HttpMethod.GET,
                    Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/exam-adjustments/{id}/refunds-due", HttpMethod.GET,
                    Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/financial-clearances/{clearanceId}/execution-starts", HttpMethod.POST,
                    Roles.SYSTEM),
            rule("/api/v1/billing/transactions/{id}/refunds", HttpMethod.POST, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/invoices/**", HttpMethod.PUT, Roles.ADMIN, Roles.CASHIER),
            rule("/api/v1/billing/revenue", HttpMethod.GET, Roles.ADMIN),
            rule("/api/v1/billing/admin/outbox/**", HttpMethod.POST, Roles.ADMIN),

            rule("/api/v1/notifications/patient/**", HttpMethod.GET, Roles.ADMIN, Roles.NURSE, Roles.PATIENT),
            rule("/api/v1/notifications/**", HttpMethod.GET, Roles.ADMIN, Roles.PATIENT),
            rule("/api/v1/notifications/send", HttpMethod.POST, Roles.ADMIN, Roles.SYSTEM),

            rule("/api/v1/reports/operations/daily", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/reports/operations/surgery", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            // Read/report roles must not leak into ADMIN-only maintenance endpoints.
            rule("/api/v1/reports/daily", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/reports/monthly", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/reports/top-medicines", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/reports/outpatient/available-periods", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/reports/outpatient/daily", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/reports/outpatient/monthly", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/reports/outpatient/statistics", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/reports/outpatient/coverage/preview", HttpMethod.GET, Roles.ADMIN),
            rule("/api/v1/reports/outpatient/coverage/acceptances", HttpMethod.POST, Roles.ADMIN),
            rule("/api/v1/reports/financial/daily", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/reports/financial/monthly", HttpMethod.GET, Roles.ADMIN, Roles.DOCTOR),
            rule("/api/v1/reports/financial/coverage", HttpMethod.GET, Roles.ADMIN),
            rule("/api/v1/reports/financial/capabilities", HttpMethod.GET, Roles.ADMIN),
            rule("/api/v1/reports/financial/rebuilds/{generationId}", HttpMethod.GET, Roles.ADMIN),
            rule("/api/v1/reports/financial/source-archives/{generationId}", HttpMethod.GET, Roles.ADMIN),
            rule("/api/v1/reports/operations/rebuilds/{id}", HttpMethod.GET, Roles.ADMIN)
    );

    private final GatewayErrorResponseWriter errors;

    public RouteAuthorizationFilter(GatewayErrorResponseWriter errors) {
        this.errors = errors;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        if (PublicEndpointPolicy.isPublic(path, method) || isInternalOnly(path)) {
            return chain.filter(exchange);
        }

        String role = exchange.getAttribute(ROLE_ATTRIBUTE);
        if (role == null) {
            return errors.write(exchange, HttpStatus.UNAUTHORIZED,
                    "AUTH_UNAUTHORIZED", "Authentication is required");
        }

        boolean allowed = method != null && RULES.stream()
                .filter(rule -> rule.method().equals(method) && rule.matches(path))
                .anyMatch(rule -> rule.roles().contains(role));
        if (!allowed) {
            return errors.write(exchange, HttpStatus.FORBIDDEN,
                    "AUTH_FORBIDDEN", "Role is not allowed to access this route");
        }
        return chain.filter(exchange);
    }

    private boolean isInternalOnly(String path) {
        return "/api/v1/org/accounts/verify".equals(path)
                || path.matches("/api/v1/org/staff/[^/]+/(exists|lookup)")
                || path.matches("/api/v1/org/departments/[^/]+/lookup")
                || path.matches("/api/v1/org/rooms/[^/]+/lookup")
                || path.matches("/api/v1/org/operating-rooms/[^/]+/lookup")
                || path.matches("/api/v1/org/staff/[^/]+/surgery-eligibility")
                || path.matches("/api/v1/patients/[^/]+/exists")
                || path.matches("/api/v1/inpatient/admissions/[^/]+/lookup");
    }

    private static RouteRule rule(String pattern, HttpMethod method, String... roles) {
        return new RouteRule(pattern, method, Set.of(roles));
    }

    private record RouteRule(String pattern, HttpMethod method, Set<String> roles) {
        private boolean matches(String path) {
            if (pattern.startsWith("/api/v2/consultation-sessions/{id}")
                    && (path.equals("/api/v2/consultation-sessions/mine") || path.startsWith("/api/v2/consultation-sessions/mine/")
                        || path.equals("/api/v2/consultation-sessions/summary") || path.startsWith("/api/v2/consultation-sessions/summary/"))) return false;
            if (pattern.startsWith("/api/v2/doctor-work-shifts/{id}")
                    && (path.equals("/api/v2/doctor-work-shifts/mine")
                        || path.startsWith("/api/v2/doctor-work-shifts/mine/"))) return false;
            if (pattern.equals("/api/v2/outpatient-registrations/{id}")
                    && Set.of("/api/v2/outpatient-registrations/mine", "/api/v2/outpatient-registrations/follow-up-sources").contains(path)) return false;
            return PATH_MATCHER.match(pattern, path);
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}

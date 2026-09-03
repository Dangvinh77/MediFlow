package com.mediflow.pharmacy.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.application.dto.request.AdjustStockRequest;
import com.mediflow.pharmacy.application.dto.request.CreateDrugRequest;
import com.mediflow.pharmacy.application.dto.response.DrugDTO;
import com.mediflow.pharmacy.application.port.in.ManageDrugUseCase;
import com.mediflow.pharmacy.domain.exception.DrugNotFoundException;
import com.mediflow.pharmacy.domain.exception.DrugRuleException;
import com.mediflow.pharmacy.infrastructure.config.SecurityConfig;
import com.mediflow.pharmacy.infrastructure.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DrugController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties =
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class DrugControllerTest {

    private static final String BASE_PATH = "/api/v1/pharmacy/drugs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ManageDrugUseCase manageDrugUseCase;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR", "PHARMACIST"})
    void getById_allowedRoles_returnsDrugEnvelope(String role) throws Exception {
        UUID drugId = UUID.randomUUID();
        when(manageDrugUseCase.getById(drugId)).thenReturn(drugDto(drugId, 25));

        mockMvc.perform(get(BASE_PATH + "/{id}", drugId)
                        .with(user("reader").roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.drugId").value(drugId.toString()))
                .andExpect(jsonPath("$.data.drugName").value("Paracetamol 500mg"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void search_validRequest_returnsPageAndPassesPageQuery() throws Exception {
        PageQuery pageQuery = new PageQuery(2, 5);
        PageResult<DrugDTO> page = PageResult.of(
                List.of(drugDto(UUID.randomUUID(), 25)), 11, 2, 5);
        when(manageDrugUseCase.search("para", pageQuery)).thenReturn(page);

        mockMvc.perform(get(BASE_PATH)
                        .param("keyword", "para")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(11))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.number").value(2))
                .andExpect(jsonPath("$.data.size").value(5));

        verify(manageDrugUseCase).search("para", pageQuery);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PHARMACIST"})
    void create_allowedRoles_returns201AndLocation(String role) throws Exception {
        UUID drugId = UUID.randomUUID();
        when(manageDrugUseCase.create(any(CreateDrugRequest.class)))
                .thenReturn(drugDto(drugId, 100));

        mockMvc.perform(post(BASE_PATH)
                        .with(user("writer").roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE_PATH + "/" + drugId))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.drugId").value(drugId.toString()));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_doctorRole_returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(manageDrugUseCase);
    }

    @Test
    @WithMockUser(roles = "PHARMACIST")
    void create_invalidBody_returns400WithFieldDetails() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "drugName": "",
                                  "unit": "",
                                  "price": -1,
                                  "stockQuantity": -5,
                                  "expiryDate": "2020-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_missingDrug_returns404() throws Exception {
        UUID drugId = UUID.randomUUID();
        when(manageDrugUseCase.getById(drugId))
                .thenThrow(new DrugNotFoundException("Không tìm thấy thuốc id=" + drugId));

        mockMvc.perform(get(BASE_PATH + "/{id}", drugId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DRUG_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PHARMACIST"})
    void adjustStock_allowedRoles_returnsUpdatedDrug(String role) throws Exception {
        UUID drugId = UUID.randomUUID();
        AdjustStockRequest request = new AdjustStockRequest(20, "Nhập thêm lô mới");
        when(manageDrugUseCase.adjustStock(drugId, request))
                .thenReturn(drugDto(drugId, 120));

        mockMvc.perform(put(BASE_PATH + "/{id}/stock", drugId)
                        .with(user("writer").roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stockQuantity").value(120));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void adjustStock_doctorRole_returns403() throws Exception {
        UUID drugId = UUID.randomUUID();

        mockMvc.perform(put(BASE_PATH + "/{id}/stock", drugId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdjustStockRequest(10, "Không được phép"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(manageDrugUseCase);
    }

    @Test
    @WithMockUser(roles = "PHARMACIST")
    void adjustStock_wouldMakeStockNegative_returns422() throws Exception {
        UUID drugId = UUID.randomUUID();
        AdjustStockRequest request = new AdjustStockRequest(-200, "Điều chỉnh kiểm kê");
        when(manageDrugUseCase.adjustStock(drugId, request))
                .thenThrow(new DrugRuleException(
                        "DRUG_OUT_OF_STOCK", "Điều chỉnh làm tồn kho âm"));

        mockMvc.perform(put(BASE_PATH + "/{id}/stock", drugId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DRUG_OUT_OF_STOCK"));
    }

    private CreateDrugRequest validCreateRequest() {
        return new CreateDrugRequest(
                "Paracetamol 500mg",
                "Paracetamol",
                "viên",
                new BigDecimal("1200.00"),
                100,
                LocalDate.now().plusYears(1),
                "Dược phẩm VN",
                20);
    }

    private DrugDTO drugDto(UUID drugId, int stockQuantity) {
        Instant now = Instant.now();
        return new DrugDTO(
                drugId,
                "Paracetamol 500mg",
                "Paracetamol",
                "viên",
                new BigDecimal("1200.00"),
                stockQuantity,
                LocalDate.now().plusYears(1),
                "Dược phẩm VN",
                20,
                now,
                now);
    }
}

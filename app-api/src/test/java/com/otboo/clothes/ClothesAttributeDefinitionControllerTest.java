package com.otboo.clothes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.clothes.entity.ClothesAttributeDefinition;
import com.otboo.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.common.test.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ClothesAttributeDefinitionControllerTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ClothesAttributeDefinitionRepository definitionRepository;

    @BeforeEach
    void setUp() {
        definitionRepository.deleteAll();
        definitionRepository.flush();
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("속성 정의 목록 API는 커서 응답과 선택값을 반환한다")
    void getsAttributeDefinitions() throws Exception {
        definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Color", List.of("Black", "White")));
        definitionRepository.saveAndFlush(
                ClothesAttributeDefinition.create("Material", List.of("Cotton")));

        mockMvc.perform(get("/api/clothes/attribute-defs")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Color"))
                .andExpect(jsonPath("$.data[0].selectableValues[0]").value("Black"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value("Color"))
                .andExpect(jsonPath("$.sortBy").value("name"))
                .andExpect(jsonPath("$.sortDirection").value("ASCENDING"))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("속성 정의 목록 API는 지원하지 않는 정렬 기준을 400으로 응답한다")
    void rejectsUnsupportedSort() throws Exception {
        mockMvc.perform(get("/api/clothes/attribute-defs")
                        .param("limit", "20")
                        .param("sortBy", "id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName")
                        .value("CLOTHES_005"));
    }
}

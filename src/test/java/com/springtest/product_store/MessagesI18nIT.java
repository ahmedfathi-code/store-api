package com.springtest.product_store;

import com.springtest.product_store.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Every kind of message in both languages: English by default, Arabic with Accept-Language: ar.
// Covers each place messages come from: controllers, validation annotations, method
// validation, the exception handler and the security filter chain.
class MessagesI18nIT extends AbstractIntegrationTest {

    private static MockHttpServletRequestBuilder arabic(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.ACCEPT_LANGUAGE, "ar");
    }

    private static final String REGISTER = "{\"email\":\"new@example.com\",\"password\":\"Passw0rd!\"}";

    @Test
    void registerSuccessAndConflict() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registered successfully"));
        mockMvc.perform(arabic(post("/api/auth/register")).contentType(MediaType.APPLICATION_JSON).content(REGISTER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("الإيميل ده مسجل قبل كده"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This email is already registered"));
    }

    @Test
    void wrongPassword() throws Exception {
        createUser("user@example.com", Role.ROLE_USER);
        String body = "{\"email\":\"user@example.com\",\"password\":\"wrong\"}";

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong email or password"));
        mockMvc.perform(arabic(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("الإيميل أو الباسورد غلط"));
    }

    @Test
    void bodyValidation() throws Exception {
        String body = "{\"email\":\"not-an-email\",\"password\":\"Passw0rd!\"}";

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is not valid"));
        mockMvc.perform(arabic(post("/api/auth/register")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("إيميل مش صحيح"));
    }

    @Test
    void pagingValidation() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size must be at most 100"));
        mockMvc.perform(arabic(get("/api/products")).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("حجم الصفحة لازم يكون 100 على الأكثر"));
    }

    @Test
    void handlerMessagesWithArguments() throws Exception {
        mockMvc.perform(arabic(get("/api/products/{id}", 999_999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("المنتج مش موجود بالـ id ده: 999999"));
        mockMvc.perform(arabic(get("/api/products")).param("sortBy", "prise"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("خاصية الترتيب 'prise' مش موجودة"));
    }

    @Test
    void securityFilterChainMessages() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
        mockMvc.perform(arabic(post("/api/products")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("لازم تسجل دخول الأول"));

        String user = bearer(createUser("user@example.com", Role.ROLE_USER));
        mockMvc.perform(arabic(post("/api/products")).header("Authorization", user)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("مش مسموح لك تعمل كده"));
    }

    @Test
    void unsupportedOrMissingLanguageFallsBackToEnglish() throws Exception {
        mockMvc.perform(get("/api/products/{id}", 999_999).header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR"))
                .andExpect(jsonPath("$.message").value("Product not found with id: 999999"));
        mockMvc.perform(get("/api/products/{id}", 999_999))
                .andExpect(jsonPath("$.message").value("Product not found with id: 999999"));
        // Arabic preferred over English when both are listed with weights
        mockMvc.perform(get("/api/products/{id}", 999_999).header(HttpHeaders.ACCEPT_LANGUAGE, "ar-EG,en;q=0.5"))
                .andExpect(jsonPath("$.message").value("المنتج مش موجود بالـ id ده: 999999"));
    }
}

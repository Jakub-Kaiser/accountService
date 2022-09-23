package com.AccountService.IntegrationTest;

import com.AccountService.DTO.UserDTO;
import com.AccountService.controller.AccountServiceController;
import com.AccountService.exception.UserExistsException;
import com.AccountService.security.UserDetailsServiceImpl;
import com.AccountService.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.jfr.Name;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import javax.print.attribute.standard.Media;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(AccountServiceController.class)
public class ControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    UserService userService;
    @MockBean
    UserDetailsServiceImpl userDetailsService;
    UserDTO returnUserWithId;
    Map<String, String> inputUser;
    Map<String, String> inputUserWrongEmail;
    Map<String, String> inputUserShortPassword;
    ObjectMapper objectMapper = new ObjectMapper();


    @BeforeEach
    void setUp() {
        inputUser = new LinkedHashMap<>();
        inputUserWrongEmail = new LinkedHashMap<>();
        inputUserShortPassword = new LinkedHashMap<>();
        inputUser.put("name", "Jakub");
        inputUser.put("lastname", "Kaiser");
        inputUser.put("email", "kuba@acme.com");
        inputUser.put("password", "111111111111");

        inputUserWrongEmail.put("name", "Jakub");
        inputUserWrongEmail.put("lastname", "Kaiser");
        inputUserWrongEmail.put("email", "kuba@gmail.com");
        inputUserWrongEmail.put("password", "111111111111");

        inputUserShortPassword.put("name", "Jakub");
        inputUserShortPassword.put("lastname", "Kaiser");
        inputUserShortPassword.put("email", "kuba@acme.com");
        inputUserShortPassword.put("password", "123");

        returnUserWithId = new UserDTO(0L, "Jakub", "Kaiser", "kuba@acme.com", "123");
    }


    @Test
    @WithMockUser
    void testAuthorizedUser() throws Exception {
        mockMvc.perform(get("/auth")).andExpect(status().isOk());
    }

    @Test
    void testUnAuthorizedUser() throws Exception {
        mockMvc.perform(get("/auth")).andExpect(status().isUnauthorized());
    }

    @Test
    void testRegisterOkPath() throws Exception {
        String inputJson = objectMapper.writeValueAsString(inputUser);
        when(userService.saveUser(any())).thenReturn(returnUserWithId);
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inputJson))
                .andExpect(status().isOk())
//                .andExpect(content().string(returnUserJson));
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Jakub"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.lastname").value("Kaiser"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.email").value("kuba@acme.com"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value("0"));

    }

    @Test
    @DisplayName("When \"name\" missing, return 400 and \"name must not be empty\"")
    void testAddUserNameMissing() throws Exception{
        inputUser.put("name", "");
        String inputJson = objectMapper.writeValueAsString(inputUser);
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inputJson))
                .andExpect(status().isBadRequest())
                .andExpect(result ->
                        assertTrue( result.getResponse().getContentAsString()
                                .contains("\"errors\":[\"name must not be empty\"]")));
    }

    @Test
    @DisplayName("When name missing and password too short" +
            ", should return 400 and include both error messages")
    void testAddUserMissingNameShortPassword() throws Exception {
        inputUser.put("name", "");
        inputUser.put("password", "123");
        String inputJson = objectMapper.writeValueAsString(inputUser);
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inputJson))
                .andExpect(status().isBadRequest())
                .andExpect(result ->
                        assertTrue(result.getResponse().getContentAsString()
                                .contains("name must not be empty")))
                .andExpect(result ->
                        assertTrue(result.getResponse().getContentAsString()
                                .contains("Password must be at least 12 characters long")));
    }

    @Test
    @DisplayName("When password too short, should return 400 and relevant message")
    void testPasswordTooShort() throws Exception {

        String inputJson = objectMapper.writeValueAsString(inputUserShortPassword);
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inputJson))
                .andExpect(status().isBadRequest())
                .andExpect(result ->
                        assertTrue(result.getResolvedException().getMessage()
                                .contains("default message [Password must be at least 12 characters long]")));
    }


    @Test
    void testRegisterWrongEmail() throws Exception {
        String inputJson = objectMapper.writeValueAsString(inputUserWrongEmail);
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inputJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRegisterUserExists() throws Exception {
        String inputJson = objectMapper.writeValueAsString(inputUser);
        when(userService.saveUser(any())).thenThrow(new UserExistsException("User exists"));
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inputJson))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof UserExistsException))
                .andExpect(result ->
                        assertEquals("User exists", result.getResolvedException().getMessage()));
    }

    @Test
    void whenEmptyJsonShouldRespondBadRequest() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenEmptyBodyShouldRespondBadRequest() throws Exception {
        mockMvc.perform(post("/register"))
                .andExpect(status().isBadRequest());
    }
}

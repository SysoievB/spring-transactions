package com.springtransactions;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService service;

    @GetMapping
    public List<User> findAll() {
        return service.getAllUsers();
    }

    @GetMapping("/{id}")
    public User findById(@PathVariable Long id) {
        return service.getUserById(id);
    }
}

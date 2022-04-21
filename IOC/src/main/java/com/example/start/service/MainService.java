package com.example.start.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MainService {
    @Autowired
    private TestService testService;

    public void mainTest() {
        System.out.println("mainTest");
        testService.test();
    }
}

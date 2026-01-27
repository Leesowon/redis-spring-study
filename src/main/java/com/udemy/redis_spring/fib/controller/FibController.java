package com.udemy.redis_spring.fib.controller;

import com.udemy.redis_spring.fib.service.FibService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("fib")
public class FibController {

    @Autowired
    private FibService service;

    @GetMapping("{index}")
    public Mono<Integer> getFib(@PathVariable int index){

        return Mono.fromSupplier(() -> this.service.getFib(index));
    }

    @GetMapping("{index}/clear")
    public Mono<Void> clearCache(@PathVariable int index){
        return Mono.fromRunnable(() -> this.service.clearCache(index));
    }

}

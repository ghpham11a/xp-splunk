package com.ghpham11a.springboot.controller;

import com.ghpham11a.springboot.dto.CreateItemRequest;
import com.ghpham11a.springboot.dto.ItemResponse;
import com.ghpham11a.springboot.dto.UpdateItemRequest;
import com.ghpham11a.springboot.service.ItemService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public List<ItemResponse> list() {
        return itemService.findAll().stream()
                .map(ItemResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ItemResponse get(@PathVariable String id) {
        return ItemResponse.from(itemService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody CreateItemRequest request) {
        ItemResponse response = ItemResponse.from(itemService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ItemResponse update(@PathVariable String id, @Valid @RequestBody UpdateItemRequest request) {
        return ItemResponse.from(itemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        itemService.delete(id);
    }
}

package com.benchmark.nosqlblocking.controller;

import com.benchmark.common.dto.CartDtos.CartItemRequest;
import com.benchmark.common.dto.CartDtos.CartResponse;
import com.benchmark.nosqlblocking.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/carts/{cartId}/items")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(@PathVariable String cartId) {
        return cartService.getCart(cartId);
    }

    @PostMapping
    public CartResponse addItem(@PathVariable String cartId, @Valid @RequestBody CartItemRequest request) {
        return cartService.addItem(cartId, request.getProductId(), request.getQuantity());
    }

    @PatchMapping
    public CartResponse updateItem(@PathVariable String cartId, @Valid @RequestBody CartItemRequest request) {
        return cartService.updateItem(cartId, request.getProductId(), request.getQuantity());
    }

    @DeleteMapping
    public CartResponse removeItem(@PathVariable String cartId,
                                   @RequestParam(required = false) String productId,
                                   @RequestBody(required = false) CartItemRequest request) {
        String target = productId != null ? productId : (request != null ? request.getProductId() : null);
        if (target == null) {
            cartService.clear(cartId);
            return CartResponse.builder().cartId(cartId).build();
        }
        return cartService.removeItem(cartId, target);
    }
}

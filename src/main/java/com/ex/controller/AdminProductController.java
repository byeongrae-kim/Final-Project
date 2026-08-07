package com.ex.controller;

import com.ex.dto.AdminProductRequest;
import com.ex.dto.ProductResponse;
import com.ex.service.AdminProductService;
import com.ex.service.AdminActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;
    private final AdminActivityService adminActivityService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
            @Valid @RequestBody AdminProductRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        ProductResponse result = adminProductService.create(request);
        record(authentication, servletRequest, "PRODUCT_CREATED", result.id(),
                "상품 '" + result.name() + "' 등록");
        return result;
    }

    @PutMapping("/{productId}")
    public ProductResponse update(
            @PathVariable(name = "productId") Long productId,
            @Valid @RequestBody AdminProductRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        ProductResponse result = adminProductService.update(productId, request);
        record(authentication, servletRequest, "PRODUCT_UPDATED", productId,
                "상품 '" + result.name() + "' 정보와 LOT 수정");
        return result;
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable(name = "productId") Long productId,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        adminProductService.delete(productId);
        record(authentication, servletRequest, "PRODUCT_DEACTIVATED", productId,
                "상품 판매 중지");
    }

    private void record(
            Authentication authentication,
            HttpServletRequest request,
            String actionType,
            Long productId,
            String description
    ) {
        adminActivityService.record(
                authentication.getName(),
                actionType,
                "PRODUCT",
                String.valueOf(productId),
                description,
                request.getRemoteAddr()
        );
    }
}

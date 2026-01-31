package com.cosmate.controller;

import com.cosmate.dto.request.AddressRequest;
import com.cosmate.dto.response.AddressResponse;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @PostMapping("")
    public ResponseEntity<ApiResponse<AddressResponse>> create(@PathVariable("userId") Integer userId, @Validated @RequestBody AddressRequest request){
        AddressResponse r = addressService.create(userId, request);
        ApiResponse<AddressResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(r);
        return ResponseEntity.ok(api);
    }

    @GetMapping("")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> list(@PathVariable("userId") Integer userId){
        List<AddressResponse> list = addressService.listAllByUser(userId);
        ApiResponse<List<AddressResponse>> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(list);
        return ResponseEntity.ok(api);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> getById(@PathVariable("userId") Integer userId, @PathVariable("id") Integer id){
        AddressResponse r = addressService.getById(userId, id);
        ApiResponse<AddressResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(r);
        return ResponseEntity.ok(api);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> update(@PathVariable("userId") Integer userId, @PathVariable("id") Integer id, @Validated @RequestBody AddressRequest request){
        AddressResponse r = addressService.update(userId, id, request);
        ApiResponse<AddressResponse> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        api.setResult(r);
        return ResponseEntity.ok(api);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("userId") Integer userId, @PathVariable("id") Integer id){
        addressService.delete(userId, id);
        ApiResponse<Void> api = new ApiResponse<>();
        api.setCode(0);
        api.setMessage("OK");
        return ResponseEntity.ok(api);
    }
}

package org.example.controller;

import lombok.Getter;
import lombok.Setter;
import org.example.service.HybridSearchService;
import org.example.service.VectorSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Paper knowledge-base APIs for direct TopK retrieval checks.
 */
@RestController
@RequestMapping("/api/papers")
public class PaperKnowledgeController {

    @Autowired
    private HybridSearchService hybridSearchService;

    @Value("${rag.top-k:5}")
    private int defaultTopK;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<VectorSearchService.SearchResult>>> search(
            @RequestParam("query") String query,
            @RequestParam(value = "topK", required = false) Integer topK) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("query 不能为空"));
        }

        int actualTopK = topK == null || topK <= 0 ? defaultTopK : Math.min(topK, 20);
        List<VectorSearchService.SearchResult> results =
                hybridSearchService.search(query.trim(), actualTopK);

        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @Getter
    @Setter
    public static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(200);
            response.setMessage("success");
            response.setData(data);
            return response;
        }

        public static <T> ApiResponse<T> error(String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(400);
            response.setMessage(message);
            return response;
        }
    }
}

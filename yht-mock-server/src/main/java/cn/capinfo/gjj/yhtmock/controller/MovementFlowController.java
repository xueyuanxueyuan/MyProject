package cn.capinfo.gjj.yhtmock.controller;

import cn.capinfo.gjj.yhtmock.model.MovementFlow;
import cn.capinfo.gjj.yhtmock.model.MovementAttempt;
import cn.capinfo.gjj.yhtmock.service.MovementNotificationService;
import cn.capinfo.gjj.yhtmock.service.MovementFlowStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/yht-mock/api/movements")
public class MovementFlowController {
    private final MovementNotificationService service;

    public MovementFlowController(MovementNotificationService service) { this.service = service; }

    @GetMapping
    public MovementFlowStore.Page query(@RequestParam(defaultValue = "") String from,
            @RequestParam(defaultValue = "") String to, @RequestParam(defaultValue = "") String bank,
            @RequestParam(defaultValue = "") String account, @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String direction, @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return service.flowStore().query(from, to, bank, account, keyword, direction, status, page, size);
    }

    @GetMapping("/{id}")
    public MovementFlow detail(@PathVariable String id) { return service.flowStore().find(id); }

    @GetMapping("/{id}/attempts")
    public List<MovementAttempt> attempts(@PathVariable String id) { return service.flowStore().attempts(id); }

    @PostMapping("/sync-history")
    public Map<String, Object> sync() { return service.syncHistory(); }

    /**
     * 历史数据统一更新：把所有超过 32 位的银行流水号收敛到 32 位以内（幂等，可重复执行）。
     * 已生成通知报文中的 yhlsh 同步改写；原值在返回结果中保留，可追溯。
     */
    @PostMapping("/normalize-serials")
    public Map<String, Object> normalizeSerials() { return service.flowStore().normalizeHistoricalSerials(); }

    @PostMapping("/{id}/generate")
    public MovementFlow generate(@PathVariable String id, @RequestBody Operation operation) {
        return service.generateMovement(id, operation.version(), operation.reason());
    }

    @PostMapping("/{id}/push")
    public MovementFlow push(@PathVariable String id, @RequestBody Operation operation) {
        return service.pushMovement(id, operation.version(), operation.confirmedNotReceived(), operation.reason());
    }

    @PostMapping("/{id}/recover")
    public MovementFlow recover(@PathVariable String id, @RequestBody Recovery operation) {
        return service.flowStore().recover(id, operation.version(), operation.confirmedSenderStopped(), operation.reason());
    }

    /**
     * 批量补生成并推送：只处理未推送（未生成 / 待推送）与推送失败的流水；
     * 已受理、发送中、历史保护、结果不明一律拒绝，不会重复动账。
     */
    @PostMapping("/batch-push")
    public Map<String, Object> batchPush(@RequestBody BatchPush operation) {
        return service.batchPush(operation.ids(), operation.reason(), operation.confirmedNotReceived());
    }

    public record Recovery(long version, boolean confirmedSenderStopped, String reason) { }

    public record BatchPush(List<String> ids, boolean confirmedNotReceived, String reason) { }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }

    public record Operation(long version, boolean confirmedNotReceived, String reason) { }
}

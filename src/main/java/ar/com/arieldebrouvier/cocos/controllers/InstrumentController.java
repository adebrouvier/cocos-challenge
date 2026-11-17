package ar.com.arieldebrouvier.cocos.controllers;

import ar.com.arieldebrouvier.cocos.dto.responses.InstrumentResponse;
import ar.com.arieldebrouvier.cocos.services.InstrumentService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    /** Search by ticker and/or name: {@code q} matches a ticker prefix or any part of the name. */
    @GetMapping("/instruments")
    public List<InstrumentResponse> search(@RequestParam @NotBlank String q) {
        return instrumentService.search(q).stream().map(InstrumentResponse::from).toList();
    }
}

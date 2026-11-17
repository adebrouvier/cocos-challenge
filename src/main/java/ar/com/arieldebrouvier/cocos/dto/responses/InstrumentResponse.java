package ar.com.arieldebrouvier.cocos.dto.responses;

import ar.com.arieldebrouvier.cocos.models.Instrument;
import ar.com.arieldebrouvier.cocos.models.InstrumentType;

public record InstrumentResponse(Integer id, String ticker, String name, InstrumentType type) {

    public static InstrumentResponse from(Instrument instrument) {
        return new InstrumentResponse(instrument.getId(), instrument.getTicker(), instrument.getName(), instrument.getType());
    }
}

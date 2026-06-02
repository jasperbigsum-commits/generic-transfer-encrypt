package io.github.jasper.transfer.encrypt.demo.dto;


import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class TransferTableDTO extends BaseDTO {
    private String from;
}

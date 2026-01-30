package cn.v7soft.accountservice.controller.resp;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class TicketResponse {
    private String ticket;
}

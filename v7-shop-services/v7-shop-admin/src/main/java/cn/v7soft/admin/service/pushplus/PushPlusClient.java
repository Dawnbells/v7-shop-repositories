package cn.v7soft.admin.service.pushplus;

public interface PushPlusClient {

    PushPlusSendResponse send(PushPlusSendRequest request);
}

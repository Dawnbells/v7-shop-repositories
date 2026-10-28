package cn.v7soft.admin.service.impl;

import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.repositories.primary.ThirdPartyWebsiteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThirdPartyWebsiteServiceDeleteTest {
    @Mock private ThirdPartyWebsiteRepository repository;

    @InjectMocks
    private ThirdPartyWebsiteService service;

    @Test
    @DisplayName("deleteAll：软删除并改写 handle/token，释放全局唯一索引以便重新添加")
    void shouldReleaseUniqueKeysOnDelete() {
        ThirdPartyWebsite website = ThirdPartyWebsite.builder().handle("shop-a").token("shpat_xxx").build();
        website.setId(42L);
        website.setStatus(StatusEnum.VALID);
        when(repository.findById(42L)).thenReturn(Optional.of(website));

        service.deleteAll(List.of(42L));

        verify(repository).saveAndFlush(website);
        assertEquals(StatusEnum.DELETED, website.getStatus());
        assertEquals("shop-a#DELETED#42", website.getHandle());
        assertEquals("DELETED:42", website.getToken());
    }
}

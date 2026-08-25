package com.tf.reader.catalogue.opds.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.catalogue.opds.dto.OpdsPublicationFeed;
import com.tf.reader.catalogue.opds.service.OpdsPublicFeedService;
import com.tf.reader.common.page.PageQuery;

@RestController
@RequestMapping("/opds/v1/public")
public class OpdsPublicCatalogueController {

    private static final String OPDS_MEDIA_TYPE = "application/opds+json";

    private final OpdsPublicFeedService publicFeedService;

    public OpdsPublicCatalogueController(OpdsPublicFeedService publicFeedService) {
        this.publicFeedService = publicFeedService;
    }

    @GetMapping(value = "/catalogue", produces = OPDS_MEDIA_TYPE)
    public OpdsPublicationFeed catalogue(PageQuery page) {
        return publicFeedService.catalogueFeed(page);
    }
}

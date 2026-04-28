package org.example.model.es;

import lombok.Data;

@Data
public class EsChunkDocument {

    private String chunkId;

    private String content;
}
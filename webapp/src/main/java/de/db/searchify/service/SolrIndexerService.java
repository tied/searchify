/*
 * Copyright (c) 2017 Redlink GmbH.
 */
package de.db.searchify.service;

import com.google.common.collect.Iterators;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 */
@Service
public class SolrIndexerService {

    private static final int BATCH_SIZE = 100;
    private static final int COMMIT_WITHIN = 500;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final SolrClient solrClient;
    private final Graph gremlin;

    private final AtomicBoolean indexing;

    private enum Relation {
        author, expert, technologies, applications, parent, knowledges
    }

    @Autowired
    public SolrIndexerService(SolrClient solrClient, Graph gremlin) {
        this.solrClient = solrClient;
        this.gremlin = gremlin;
        indexing = new AtomicBoolean(false);
    }

    public void index() {
        if (indexing.compareAndSet(false, true)) {
            try {
                solrClient.deleteByQuery("*:*");
                Iterators.partition(gremlin.vertices(), BATCH_SIZE)
                        .forEachRemaining(p -> {
                            final Set<SolrInputDocument> docs = p.stream()
                                    .map(this::vertex2SolrInputDocument)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet());
                            if (!docs.isEmpty()) {
                                try {
                                    solrClient.add(docs, COMMIT_WITHIN);
                                } catch (SolrServerException | IOException e) {
                                    log.error("Could not send {} documents to solr: {}", docs.size(), e.getMessage(), e);
                                }
                            }
                        });
                solrClient.commit();

                // TODO: generate the treeview.json
            } catch (IOException | SolrServerException e) {
                log.warn("Error during solrClient.commit() - it could take a while until changes are visible", e);
            } finally {
                indexing.set(false);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.warn("Indexing already in progress", new Throwable("Indexing already in progress, callstack"));
            } else {
                log.warn("Indexing already in progress");
            }
        }
    }

    protected SolrInputDocument vertex2SolrInputDocument(Vertex v) {
        final SolrInputDocument doc = new SolrInputDocument();
        // TODO: create a SolrInputDocument from the Vertex
        doc.setField("id", T.id.apply(v));

        v.properties().forEachRemaining(prop -> {
            if(!prop.key().endsWith("_tdt")) {
                doc.setField(prop.key(), prop.value());
            }
        });

        v.edges(Direction.OUT).forEachRemaining(edge -> {
            doc.addField("relation_uri_" + edge.label() + "_ss", edge.inVertex().property("id").value());
            doc.addField("relation_combi_" + edge.label() + "_ss", edge.inVertex().property("id").value() + " ::: " + edge.inVertex().property("dbsearch_title_s").value()); //TODO should be a complex field
            doc.addField("relation_name_" + edge.label() + "_txt", edge.inVertex().property("dbsearch_title_s").value());
        });

        v.edges(Direction.IN).forEachRemaining(edge -> {
            doc.addField("relation_uri_" + edge.label() + "_of_ss", edge.outVertex().property("id").value());
            doc.addField("relation_combi_" + edge.label() + "_of_ss", edge.outVertex().property("id").value() + " ::: " + edge.outVertex().property("dbsearch_title_s").value()); //TODO should be a complex field
            doc.addField("relation_name_" + edge.label() + "_of_txt", edge.outVertex().property("dbsearch_title_s").value());
        });

        return doc;
    }

}

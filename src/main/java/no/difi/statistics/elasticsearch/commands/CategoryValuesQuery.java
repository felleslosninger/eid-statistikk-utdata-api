package no.difi.statistics.elasticsearch.commands;

import no.difi.statistics.model.CategoryValues;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.*;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CategoryValuesQuery {

    private RestHighLevelClient elasticSearchClient;

    // Search for year in index-name (to remove it).
    private static final String yearRegex = "\\d{4}$";
    private static final Pattern yearPattern = Pattern.compile(yearRegex);

    // Example of an index-name: 991825827@idporten-innlogging@hour2022
    private static final String searchTerm = "*@*@*";

    private static final Logger logger = LoggerFactory.getLogger(CategoryValuesQuery.class);

    private CategoryValuesQuery() { }

    public Set<CategoryValues> execute() throws IOException {

        // Get categories as key, index-name as value from ES.
        HashMap<Map<String, Object>, CategoryValues> categoriesAndIndexNameMap = getCategoriesAsKeyFromES();

        // Insert into map using index-name as key, categories as value, i.e. swap above key/value.
        Map<CategoryValues, Set<Map<String, Object>>> categoryValuesMap = getIndexNameAsKey(categoriesAndIndexNameMap);
        logger.info("categoryValuesMap: {}", categoryValuesMap);

        // Merge key/value into a record.
        return mergeKeyAndValue(categoryValuesMap);
    }

    private HashMap<Map<String, Object>, CategoryValues> getCategoriesAsKeyFromES() throws IOException {
        /*
        Traverse result from ES and put into a HashMap and use categories as key, indexname as value.
        This will filter away any duplicate categories.
        When done traverse HashMap, switch key and value and put into another HashMap.
        Finally, traverse last HashMap and put into CategoryValues which is then returned.

        From

        [{
            "_index": "991825827@idporten-innlogging@hour2022",
            "_source":
            {
                "category.TE": "Akershus universitetssykehus hf",
                "category.TL-entityId": "idfed.ad.ahus.no",
                "category.TE-orgnum": "983971636",
                "category.TL-orgnum": "983971636",
                "category.TE-entityId": "idfed.ad.ahus.no",
                "category.TL": "Akershus universitetssykehus hf"
            }
        },
        {
            "_index": "991825827@idporten-innlogging@hour2022",
            "_source":
            {
                "category.TE": "Altinn",
                "category.TL-entityId": "sp.altinn.no",
                "category.TE-orgnum": "991825827",
                "category.TL-orgnum": "991825827",
                "category.TE-entityId": "sp.altinn.no",
                "category.TL": "Altinn"
            }
        }]


        to

        [{
            "owner": "991825827",
            "name": "idporten-innlogging",
            "distance": "hour2022",
            "categories": [
                {
                    "category.TE": "Akershus universitetssykehus hf",
                    "category.TL-entityId": "idfed.ad.ahus.no",
                    "category.TE-orgnum": "983971636",
                    "category.TL-orgnum": "983971636",
                    "category.TE-entityId": "idfed.ad.ahus.no",
                    "category.TL": "Akershus universitetssykehus hf"
                },
                {
                    "category.TE": "Altinn",
                    "category.TL-entityId": "sp.altinn.no",
                    "category.TE-orgnum": "991825827",
                    "category.TL-orgnum": "991825827",
                    "category.TE-entityId": "sp.altinn.no",
                    "category.TL": "Altinn"
                }
            ]
        }]

         */

        HashMap<Map<String, Object>, CategoryValues> categoriesAndIndexNameMap = new HashMap<>();

        // Get a cursor to scroll through the results, set a size for each batch. And a timeout of 1 minute.
        // https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/java-rest-high-search-scroll.html
        final Scroll scroll = new Scroll(TimeValue.timeValueMinutes(1L));
        SearchRequest searchRequest = new SearchRequest(searchTerm);
        searchRequest.scroll(scroll);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        String[] includeFields = new String[] {"category.*"};
        String[] excludeFields = new String[] {""};
        searchSourceBuilder.fetchSource(includeFields, excludeFields);
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        searchSourceBuilder.size(10000);
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = elasticSearchClient.search(searchRequest, RequestOptions.DEFAULT);
        String scrollId = searchResponse.getScrollId();
        SearchHits hits = searchResponse.getHits();
        TotalHits totalHits = hits.getTotalHits();

        logger.info("total hits: {}", totalHits);

        SearchHit[] searchHits = hits.getHits();
        while (searchHits != null && searchHits.length > 0) {
            for (SearchHit hit : searchHits) {
                //logger.info("search-hit: {}", hit);
                // 991825827@idporten-innlogging@hour2022
                String[] index = hit.getIndex().split("@", 3);
                if (index.length == 3) {
                    Map<String, Object> sortedSourceMap = new TreeMap<>(hit.getSourceAsMap());
                    String distance = getDistance(index[2]);

                    Set<Map<String, Object>> empty = new HashSet<>();
                    CategoryValues indexName = new CategoryValues(index[0], index[1], distance, empty);
                    categoriesAndIndexNameMap.put(sortedSourceMap, indexName);
                }
            }

            SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
            scrollRequest.scroll(scroll);
            searchResponse = elasticSearchClient.scroll(scrollRequest, RequestOptions.DEFAULT);
            scrollId = searchResponse.getScrollId();
            searchHits = searchResponse.getHits().getHits();
        }

        ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
        clearScrollRequest.addScrollId(scrollId);
        ClearScrollResponse clearScrollResponse = elasticSearchClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
        boolean succeeded = clearScrollResponse.isSucceeded();
        logger.info("succeeded: {}", succeeded);
        logger.info("categoriesAndIndexNameMap:\n{}", categoriesAndIndexNameMap);
        logger.info("categoriesAndIndexNameMap size: {}", categoriesAndIndexNameMap.size());

        return categoriesAndIndexNameMap;
    }

    private Map<CategoryValues, Set<Map<String, Object>>> getIndexNameAsKey(HashMap<Map<String, Object>, CategoryValues> categoriesAndIndexNameMap) {
        // Insert into map using ES index-name as key, categories as value.
        Map<CategoryValues, Set<Map<String, Object>>> categoryValuesMap = new HashMap<>();
        for (Map.Entry<Map<String, Object>, CategoryValues> mapCategoryValuesEntry : categoriesAndIndexNameMap.entrySet()) {
            Map<String, Object> categoryKey = mapCategoryValuesEntry.getKey();
            CategoryValues categoryValues = mapCategoryValuesEntry.getValue();
            logger.info("categoryKey: {}", categoryKey);
            logger.info("categoryValues: {}", categoryValues);
            if (categoryValuesMap.containsKey(categoryValues)) {
                categoryValuesMap.get(categoryValues).add(categoryKey);
            } else {
                Set<Map<String, Object>> arrayListList = new HashSet<>(Collections.singleton(categoryKey));
                categoryValuesMap.put(categoryValues, arrayListList);
            }
        }

        return  categoryValuesMap;
    }

    private Set<CategoryValues> mergeKeyAndValue(Map<CategoryValues, Set<Map<String, Object>>> categoryValuesMap) {
        Set<CategoryValues> categoryValuesSet = new HashSet<>();
        for (Map.Entry<CategoryValues, Set<Map<String, Object>>> categoryValuesMapEntry : categoryValuesMap.entrySet()) {
            String owner = categoryValuesMapEntry.getKey().getOwner();
            String name = categoryValuesMapEntry.getKey().getName();
            String distance = categoryValuesMapEntry.getKey().getDistance();
            Set<Map<String, Object>> value = categoryValuesMapEntry.getValue();
            logger.info("key: {}", categoryValuesMapEntry.getKey());
            logger.info("value: {}", value);
            categoryValuesSet.add(new CategoryValues(owner, name, distance, categoryValuesMapEntry.getValue()));
        }

        return categoryValuesSet;
    }

    private String getDistance(String d) {
        Matcher matcher = yearPattern.matcher(d);
        String distance = "hour";
        if (matcher.find()) {
            distance = d.substring(0, matcher.start());
        }

        if ("hour".equals(distance)) {
            distance = "hours";
        } else {
            distance = "minutes";
        }

        return distance;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final CategoryValuesQuery instance = new CategoryValuesQuery();

        public Builder elasticsearchClient(RestHighLevelClient client) {
            instance.elasticSearchClient = client;
            return this;
        }

        public CategoryValuesQuery build() {
            return instance;
        }

    }
}

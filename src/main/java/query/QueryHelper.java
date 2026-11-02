package query;

import java.util.List;

public class QueryHelper {

   // ── Count queries (size:0) ──────────────────────────────────────────────

   public static String generateCountQuery(String index) {
      return String.format("""
                   {
                       "params": {
                           "index": "%s",
                           "type": "_doc",
                           "esQuery": {
                               "size": 0,
                               "track_total_hits": true,
                               "query": { "match_all": {} }
                           }
                       }
                   }
            """, index);
   }

   public static String generateCountQueryByType(String index, String type) {
      return String.format("""
                   {
                       "params": {
                           "index": "%s",
                           "type": "_doc",
                           "esQuery": {
                               "size": 0,
                               "track_total_hits": true,
                               "query": {
                                   "bool": {
                                       "filter": [
                                           { "terms": { "do.dt.kw": ["%s"] } }
                                       ]
                                   }
                               }
                           }
                       }
                   }
            """, index, type);
   }

   // ── Type discovery via aggregation ──────────────────────────────────────
   // Discovers all distinct do.dt.kw values so copyAll can shard by type,
   // staying under the ES max_result_window for each individual query.

   public static String generateTypesAggQuery(String index) {
      return String.format("""
                   {
                       "params": {
                           "index": "%s",
                           "type": "_doc",
                           "esQuery": {
                               "size": 0,
                               "aggs": {
                                   "types": {
                                       "terms": {
                                           "field": "do.dt.kw",
                                           "size": 10000
                                       }
                                   }
                               }
                           }
                       }
                   }
            """, index);
   }

   // ── Full-doc batch fetch with from/size ──────────────────────────────────

   public static String generateIdPrefixCountQuery(String index, String idPrefix) {
      return String.format("""
                   {
                       "params": {
                           "index": "%s",
                           "type": "_doc",
                           "esQuery": {
                               "size": 0,
                               "track_total_hits": true,
                               "query": {
                                   "prefix": { "id": "%s" }
                               }
                           }
                       }
                   }
            """, index, idPrefix);
   }

   /** Fetches docs whose 'id' field starts with the given prefix, avoiding sort/aggs.
    *  Each first-letter bucket stays well under max_result_window. */
   public static String generateIdPrefixDocsBatch(String index, String idPrefix, int from, int size) {
      return String.format("""
                   {
                       "params": {
                           "index": "%s",
                           "type": "_doc",
                           "esQuery": {
                               "from": %d,
                               "size": %d,
                               "query": {
                                   "prefix": { "id": "%s" }
                               }
                           }
                       }
                   }
            """, index, from, size, idPrefix);
   }

   public static String generateAllDocsBatch(String index, int from, int size) {
      return String.format("""
                   {
                       "params": {
                           "index": "%s",
                           "type": "_doc",
                           "esQuery": {
                               "from": %d,
                               "size": %d,
                               "track_total_hits": true,
                               "query": { "match_all": {} }
                           }
                       }
                   }
            """, index, from, size);
   }

   public static String generateTypeDocsBatch(String index, String type, int from, int size) {
      return String.format("""
                   {
                       "params": {
                           "index": "%s",
                           "type": "_doc",
                           "esQuery": {
                               "from": %d,
                               "size": %d,
                               "track_total_hits": true,
                               "query": {
                                   "bool": {
                                       "filter": [
                                           { "terms": { "do.dt.kw": ["%s"] } }
                                       ]
                                   }
                               }
                           }
                       }
                   }
            """, index, from, size, type);
   }

   // ── ID lookup (single document) ─────────────────────────────────────────

   public static String generateIdSearchQuery(String id, String index) {
      return String.format("""
                {
                    "params": {
                        "esQuery": {
                            "track_total_hits": true,
                            "query": {
                                "match": {
                                    "_id": "%s"
                                }
                            }
                        },
                        "index": "%s",
                        "type": "_doc"
                    }
                }
            """, id, index);
   }
}

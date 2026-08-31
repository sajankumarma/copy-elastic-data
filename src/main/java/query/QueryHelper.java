package query;

public class QueryHelper {
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

   public static String generateTypeSearchQuery(String index, String type) {
      return String.format("""
                   {
                       "params": {
                           "index": "%s",
                           "type": "_doc",
                           "esQuery": {
                               "size": 30000,
                               "query": {
                                   "bool": {
                                       "filter": [
                                           {
                                               "terms": {
                                                   "do.dt.kw": [
                                                       "%s"
                                                   ]
                                               }
                                           }
                                       ]
                                   }
                               }
                           }
                       }
                   }
            """, index, type);
   }

}

package objects;

import java.util.List;

public class IndexConfig {

    private List<Index> indexes;

    // Getters and Setters
    public List<Index> getIndexes() {
        return indexes;
    }

    public void setIndexes(List<Index> indexes) {
        this.indexes = indexes;
    }

    // Inner classes
    public static class Index {
        private String indexName;
        private List<String> dataObjectTypes;
        private List<DataObject> dataObjects;

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }

        public List<String> getDataObjectTypes() {
            return dataObjectTypes;
        }

        public void setDataObjectTypes(List<String> dataObjectTypes) {
            this.dataObjectTypes = dataObjectTypes;
        }

        public List<DataObject> getDataObjects() {
            return dataObjects;
        }

        public void setDataObjects(List<DataObject> dataObjects) {
            this.dataObjects = dataObjects;
        }
    }

    public static class DataObject {
        private String dataObjectType;
        private List<String> ids;

        public String getDataObjectType() {
            return dataObjectType;
        }

        public void setDataObjectType(String dataObjectType) {
            this.dataObjectType = dataObjectType;
        }

        public List<String> getIds() {
            return ids;
        }

        public void setIds(List<String> ids) {
            this.ids = ids;
        }
    }
}


package com.torodb.integration.backend;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.StreamSupport;

import org.jooq.DSLContext;

import com.torodb.backend.SqlHelper;
import com.torodb.backend.SqlInterface;
import com.torodb.backend.TableRefComparator;
import com.torodb.backend.d2r.R2DBackendTranslatorImpl;
import com.torodb.core.TableRefFactory;
import com.torodb.core.d2r.CollectionData;
import com.torodb.core.d2r.D2RTranslator;
import com.torodb.core.d2r.DocPartData;
import com.torodb.core.d2r.DocPartResults;
import com.torodb.core.d2r.IdentifierFactory;
import com.torodb.core.d2r.R2DTranslator;
import com.torodb.core.document.ToroDocument;
import com.torodb.core.transaction.metainf.MetaCollection;
import com.torodb.core.transaction.metainf.MetaDatabase;
import com.torodb.core.transaction.metainf.MutableMetaDatabase;
import com.torodb.core.transaction.metainf.MutableMetaSnapshot;
import com.torodb.d2r.D2RTranslatorStack;
import com.torodb.d2r.IdentifierFactoryImpl;
import com.torodb.d2r.MockIdentifierInterface;
import com.torodb.d2r.MockRidGenerator;
import com.torodb.d2r.R2DBackedTranslator;
import com.torodb.kvdocument.conversion.json.JacksonJsonParser;
import com.torodb.kvdocument.conversion.json.JsonParser;
import com.torodb.kvdocument.values.KVDocument;

public class BackendDocumentTestHelper {
	
    private SqlInterface sqlInterface;
    private SqlHelper sqlHelper;
	private TableRefFactory tableRefFactory;
	private TestSchema schema;
	
	private MockRidGenerator ridGenerator = new MockRidGenerator();
	private IdentifierFactory identifierFactory = new IdentifierFactoryImpl(new MockIdentifierInterface());
	
	public BackendDocumentTestHelper(SqlInterface sqlInterface, SqlHelper sqlHelper, TableRefFactory tableRefFactory, TestSchema schema){
		this.sqlInterface = sqlInterface;
		this.sqlHelper = sqlHelper;
		this.tableRefFactory = tableRefFactory;
		this.schema = schema;
	}

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Collection<ToroDocument> readDocuments(MetaDatabase metaDatabase, MetaCollection metaCollection,
            DocPartResults<ResultSet> docPartResultSets) {
        R2DTranslator r2dTranslator = new R2DBackedTranslator(new R2DBackendTranslatorImpl(sqlInterface, sqlHelper, metaDatabase, metaCollection));
        Collection<ToroDocument> readedDocuments = r2dTranslator.translate(docPartResultSets);
        return readedDocuments;
    }

    public List<Integer> writeCollectionData(DSLContext dsl, CollectionData collectionData) {
        Iterator<DocPartData> docPartDataIterator = StreamSupport.stream(collectionData.spliterator(), false)
                .iterator();
        List<Integer> generatedDids = new ArrayList<>();
        while (docPartDataIterator.hasNext()) {
            DocPartData docPartData = docPartDataIterator.next();
            if (docPartData.getMetaDocPart().getTableRef().isRoot()) {
                docPartData.forEach(docPartRow ->generatedDids.add(docPartRow.getDid()));
            }
            sqlInterface.getWriteInterface().insertDocPartData(dsl, schema.database.getIdentifier(), docPartData);
        }
        return generatedDids;
    }

    public CollectionData parseDocumentAndCreateDocPartDataTables(MutableMetaSnapshot mutableSnapshot, DSLContext dsl, KVDocument document)
            throws Exception {
    	return parseDocumentsAndCreateDocPartDataTables(mutableSnapshot, dsl, Arrays.asList(document));
    }

    public CollectionData parseDocumentsAndCreateDocPartDataTables(MutableMetaSnapshot mutableSnapshot, DSLContext dsl, List<KVDocument> documents)
            throws Exception {
        CollectionData collectionData = readDataFromDocuments(schema.database.getName(), schema.collection.getName(), documents, mutableSnapshot);
        mutableSnapshot.streamMetaDatabases().forEachOrdered(metaDatabase -> {
            metaDatabase.streamMetaCollections().forEachOrdered(metaCollection -> {
                metaCollection.streamContainedMetaDocParts().sorted(TableRefComparator.MetaDocPart.ASC).forEachOrdered(metaDocPart -> {
                    if (metaDocPart.getTableRef().isRoot()) {
                        sqlInterface.getStructureInterface().createRootDocPartTable(dsl, schema.database.getIdentifier(), metaDocPart.getIdentifier(), metaDocPart.getTableRef());
                    } else {
                        sqlInterface.getStructureInterface().createDocPartTable(dsl, schema.database.getIdentifier(), metaDocPart.getIdentifier(), metaDocPart.getTableRef(),
                                metaCollection.getMetaDocPartByTableRef(metaDocPart.getTableRef().getParent().get()).getIdentifier());
                    }
                    metaDocPart.streamScalars().forEachOrdered(metaScalar -> {
                        sqlInterface.getStructureInterface().addColumnToDocPartTable(dsl, schema.database.getIdentifier(), metaDocPart.getIdentifier(), 
                                metaScalar.getIdentifier(), sqlInterface.getDataTypeProvider().getDataType(metaScalar.getType()));
                    });
                    metaDocPart.streamFields().forEachOrdered(metaField -> {
                        sqlInterface.getStructureInterface().addColumnToDocPartTable(dsl, schema.database.getIdentifier(), metaDocPart.getIdentifier(), 
                                metaField.getIdentifier(), sqlInterface.getDataTypeProvider().getDataType(metaField.getType()));
                    });
                });
            });
        });
        return collectionData;
    }
    
    public KVDocument parseFromJson(String jsonFileName) throws Exception {
        JsonParser parser = new JacksonJsonParser();
        return parser.createFromResource("docs/" + jsonFileName);
    }
    
    public List<KVDocument> parseListFromJson(String jsonFileName) throws Exception {
        JsonParser parser = new JacksonJsonParser();
        return parser.createListFromResource("docs/" + jsonFileName);
    }
    
    public CollectionData readDataFromDocuments(String database, String collection, List<KVDocument> documents, MutableMetaSnapshot mutableSnapshot) throws Exception {
        MutableMetaDatabase db = mutableSnapshot.getMetaDatabaseByName(database);
        D2RTranslator translator = new D2RTranslatorStack(tableRefFactory, identifierFactory, ridGenerator, db, db.getMetaCollectionByName(collection));
        documents.forEach(translator::translate);
        return translator.getCollectionDataAccumulator();
    }
    
}

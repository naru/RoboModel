package com.nonninz.robomodel;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.test.AndroidTestCase;

import com.nonninz.robomodel.exceptions.InstanceNotFoundException;

public class ModelTestCase extends AndroidTestCase {

    private RoboManager<TestModel> mManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mManager = RoboManager.get(getContext(), TestModel.class);
        getContext().deleteDatabase(mManager.getDatabaseName());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        mManager = null;
    }

    public void testSaveModel() {
        TestModel testModel = new TestModel(getContext());
        getContext().deleteDatabase(testModel.getDatabaseName());
        testModel.springField = "Hello!";

        testModel.save();

        SQLiteDatabase db = mContext.openOrCreateDatabase(testModel.getDatabaseName(),
                        Context.MODE_PRIVATE, null);;
        Cursor cursor = db.rawQuery("SELECT * FROM TestModel", null);
        assertEquals(1, cursor.getCount());

        cursor.moveToFirst();
        assertEquals("Hello!", cursor.getString(cursor.getColumnIndex("springField")));
    }

    public void testSaveSeveralModels() {
        RoboManager<ParentTestModel> parentManager = RoboManager.get(getContext(),
                        ParentTestModel.class);

        parentManager.create().save();
        parentManager.create().save();
        mManager.create().save();
        mManager.create().save();
        mManager.create().save();

        assertEquals(2, parentManager.all().size());
        assertEquals(3, mManager.all().size());
    }

    public void testSaveTree() {
        RoboManager<ParentTestModel> parentManager = RoboManager.get(getContext(),
                        ParentTestModel.class);
        ParentTestModel parent = parentManager.create();

        for (int i = 0; i < 3; i++) {
            TestModel child = mManager.create();
            parent.testModels.add(child);
        }

        parent.save();

        // Test that both parent and children gets written to DB
        assertEquals(1, parentManager.all().size());
        assertEquals(3, mManager.all().size());

        // Test that children gets a reference to the parent
        for (TestModel child : parent.testModels) {
            assertEquals(parent, child.parent);
        }
    }

    public void testLoadTree() throws InstanceNotFoundException {
        RoboManager<ParentTestModel> parentManager = RoboManager.get(getContext(),
                        ParentTestModel.class);
        ParentTestModel parent = parentManager.create();

        for (int i = 0; i < 3; i++) {
            TestModel child = mManager.create();
            parent.testModels.add(child);
        }

        parent.save();

        ParentTestModel loadedParent = parentManager.last();

        // Test that parent loaded the children
        assertEquals(3, loadedParent.testModels.size());

        // Test for backreference to the parent
        for (TestModel child : loadedParent.testModels) {
            assertEquals(parent, child.parent);
        }
    }

    public void testReload() throws InstanceNotFoundException {
        TestModel model = mManager.create();
        model.save();

        TestModel loaded = mManager.last();
        loaded.springField = "Modified";

        loaded.save();
        model.reload();

        assertEquals("Modified", model.springField);
    }

    public void testToJson() {
        TestModel model = mManager.create();
        assertEquals(String.class, model.toJson().getClass());
    }

    public void testCreateFromJson() throws InstanceNotFoundException {
        String json = "{" +
                        "springField: \"Hello1212\"," +
                        "intField: 53252" +
                        "}";

        TestModel model = mManager.create(json);
        model.save();

        TestModel saved = mManager.last();

        assertEquals("Hello1212", saved.springField);
        assertEquals(53252, saved.intField);
    }

    public void testCreateTreeFromJson() throws InstanceNotFoundException {
        String json = "{" +
                        "test: \"11\"," +
                        "testModels:" +
                        "[{" +
                        "springField: \"Hello1212\"," +
                        "intField: 53252" +
                        "}]" +
                        "}";

        RoboManager<ParentTestModel> parentManager = RoboManager.get(getContext(),
                        ParentTestModel.class);
        ParentTestModel model = parentManager.create(json);
        model.save();

        assertEquals(1, parentManager.all().size());
        assertEquals(1, mManager.all().size());

        ParentTestModel saved = parentManager.last();

        assertEquals("11", saved.test);
        assertEquals(1, saved.testModels.size());

        assertEquals("Hello1212", saved.testModels.get(0).springField);
        assertEquals(53252, saved.testModels.get(0).intField);
    }

    public void testCreateMultiLevelTreeFromJson() throws InstanceNotFoundException {
        String json = "{" +
                        "parentTestModels:" +
                        "[{" +
                        "test: \"11\"," +
                        "testModels:" +
                        "[{" +
                        "springField: \"Hello1212\"," +
                        "intField: 53252" +
                        "}]" +
                        "}]" +
                        "}";

        RoboManager<ParentTestModel2> parentManager = RoboManager.get(getContext(),
                        ParentTestModel2.class);
        ParentTestModel2 model = parentManager.create(json);
        model.save();

        assertEquals(1, parentManager.all().size());
        assertEquals(1, mManager.all().size());

        ParentTestModel2 saved = parentManager.last();

        assertEquals(1, saved.parentTestModels.size());

        assertEquals(1, saved.parentTestModels.get(0).testModels.size());
    }

}

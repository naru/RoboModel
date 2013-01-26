package com.nonninz.robomodel;

import android.test.AndroidTestCase;

import com.nonninz.robomodel.RoboManager;
import com.nonninz.robomodel.exceptions.InstanceNotFoundException;

public class ModelTestCase extends AndroidTestCase {

    private RoboManager<TestModel> mManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mManager = RoboManager.get(getContext(), TestModel.class);
        mManager.dropDatabase();
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        
        mManager = null;
    }


    public void testSaveSeveralModels() {
        RoboManager<ParentTestModel> parentManager = RoboManager.get(getContext(), ParentTestModel.class);

        parentManager.create().save();
        parentManager.create().save();
        mManager.create().save();
        mManager.create().save();
        mManager.create().save();
        
        assertEquals(2, parentManager.all().size());
        assertEquals(3, mManager.all().size());
    }
    
    public void testSaveTree() {
        RoboManager<ParentTestModel> parentManager = RoboManager.get(getContext(), ParentTestModel.class);
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
        for (TestModel child: parent.testModels) {
            assertEquals(parent, child.parent);
        }
    }
    
    public void testLoadTree() throws InstanceNotFoundException {
        RoboManager<ParentTestModel> parentManager = RoboManager.get(getContext(), ParentTestModel.class);
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
        for (TestModel child: loadedParent.testModels) {
            assertEquals(parent, child.parent);
        }
    }
    
    public void testToJson() {
        TestModel model = mManager.create();
        assertEquals(String.class, model.toJson().getClass());
    }
}

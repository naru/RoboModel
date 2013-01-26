/**
 * Copyright 2012 Francesco Donadon
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nonninz.robomodel;

import static android.provider.BaseColumns._ID;

import java.lang.reflect.Field;
import java.util.List;

import roboguice.util.Ln;
import android.content.Context;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.nonninz.robomodel.annotations.BelongsTo;
import com.nonninz.robomodel.annotations.HasMany;

class DatabaseManager {
    public static String where(long id) {
        return _ID + " = " + id;
    }

    public static String getTypeForField(Field field) {
      final Class<?> type = field.getType();

      if (type == String.class) {
          return "TEXT";
      } else if (type == Boolean.TYPE) {
          return "BOOLEAN";
      } else if (type == Byte.TYPE) {
          return "INTEGER";
      } else if (type == Double.TYPE) {
          return "REAL";
      } else if (type == Float.TYPE) {
          return "REAL";
      } else if (type == Integer.TYPE) {
          return "INTEGER";
      } else if (type == Long.TYPE) {
          return "INTEGER";
      } else if (type == Short.TYPE) {
          return "INTEGER";
      } else if (type.isEnum()) {
          return "TEXT";
      } else if (field.isAnnotationPresent(BelongsTo.class)) {
          return "INTEGER";
      }
      else {
          return "TEXT";
      }
    }
  
    private final Context mContext;

    /**
     * @param context
     */
    public DatabaseManager(Context context) {
        mContext = context;
    }

    /**
     * @param tableName
     * @param column
     * @param type
     * @param db
     */
    private void addColumn(String tableName, String column, String type, SQLiteDatabase db) {
        final String sql = String.format("ALTER TABLE %s ADD %s %s;", tableName, column,
                        type);
        db.execSQL(sql);
    }

    private long insertOrUpdate(String tableName, TypedContentValues values, long id,
                    SQLiteDatabase database) {
        if (id == RoboModel.UNSAVED_MODEL_ID) {
            return database.insertOrThrow(tableName, null, values.toContentValues());
        } else {
            database.update(tableName, values.toContentValues(), where(id), null);
            return id;
        }
    }

    /**
     * Creates the table or populates it with missing fields
     * 
     * @param tableName
     *            The name of the table
     * @param values
     *            The columns of the table
     * @param db
     *            The database where the table is situated
     * @throws SQLException
     *             if it cannot create the table
     */
    void createOrPopulateTable(String tableName, List<Field> fields,
                    SQLiteDatabase db) {
      
        Ln.d("Fixing table %s", tableName);

        // Check if table exists
        try {
            DatabaseUtils.queryNumEntries(db, tableName);
        } catch (final SQLiteException ex) {
            // If it doesn't, create it and return
            createTable(tableName, fields, db);
            return;
        }

        // Otherwise, check if all fields exist, add if needed
        for (final Field field : fields) {
            try {
                String sql = String.format("select typeof (%s) from %s", field.getName(), tableName);
                db.rawQuery(sql, null);
            } catch (final SQLiteException e) {
                Ln.d("Adding column %s %s", field.getName(), getTypeForField(field));
                addColumn(tableName, field.getName(), getTypeForField(field), db);
            }
        }
    }

    /**
     * @param tableName
     * @param values
     * @param db
     * @return
     */
    private void createTable(String tableName, List<Field> fields, SQLiteDatabase db) {
        final StringBuilder sql = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");

        for (final Field field : fields) {
            sql.append(field.getName()).append(" ").append(getTypeForField(field)).append(", ");
        }
        sql.append(_ID).append(" integer primary key autoincrement);");
        Ln.d("Creating table: %s", sql.toString());
        db.execSQL(sql.toString());
    }
    
    /**
     * @param databaseName
     * @param tableName
     */
    public void deleteAllRecords(String databaseName, String tableName) {
        final SQLiteDatabase db = openOrCreateDatabase(databaseName);
        db.delete(tableName, null, null);
        db.close();
    }

    void deleteRecord(String databaseName, String tableName, long id) {
        final SQLiteDatabase db = openOrCreateDatabase(databaseName);
        db.delete(tableName, where(id), null);
        db.close();
    }

    SQLiteDatabase openOrCreateDatabase(String databaseName) {
        return mContext.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null);
    }

    void saveModel(RoboModel model) {
        final SQLiteDatabase database = openOrCreateDatabase(model.getDatabaseName());

        List<Field> fields = model.getSavedFields();
        final TypedContentValues cv = new TypedContentValues(fields.size());
        for (final Field field : fields) {
            model.saveField(field, cv);
        }
        
        // For optimizing speed, first try to save it. Then deal with errors (like table/field not existing);
        try {
            model.mId = insertOrUpdate(model.getTableName(), cv, model.mId, database);
        } catch (final SQLiteException ex) {
            createOrPopulateTable(model.getTableName(), fields, database);
            model.mId = insertOrUpdate(model.getTableName(), cv, model.mId, database);
        } finally {
            database.close();
        }
        
        // Save children
        saveChildModels(model);
    }

    /**
     * Saves referenced child RoboModels annotated with @HasMany
     *  
     * @param model
     */
    private void saveChildModels(RoboModel model) {
        Field[] fields = model.getClass().getFields();
        for (Field field: fields) {
            if (field.isAnnotationPresent(HasMany.class)) {
                final boolean wasAccessible = field.isAccessible();
                field.setAccessible(true);
                
                try {
                    Class<? extends RoboModel> referencedModel = field.getAnnotation(HasMany.class).value();
                    if (Iterable.class.isAssignableFrom(field.getType())) {
                        Iterable<?> list = (Iterable<?>) field.get(model); 
                        for (Object item: list) {
                            RoboModel cast = referencedModel.cast(item);
                            cast.deepSave(model);
                        }
                    } else {
                        //TODO ??
                    }
                } catch (IllegalAccessException e) {
                    // Can't happen
                } finally {
                    field.setAccessible(wasAccessible);
                }
            }
        }
    } 
}

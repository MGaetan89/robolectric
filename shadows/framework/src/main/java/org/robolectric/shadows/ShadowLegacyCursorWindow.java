package org.robolectric.shadows;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.database.Cursor;
import android.database.CursorWindow;
import com.almworks.sqlite4java.SQLiteConstants;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Legacy shadow for {@link CursorWindow}. */
@Implements(value = CursorWindow.class, isInAndroidSdk = false)
public class ShadowLegacyCursorWindow extends ShadowCursorWindow {
  private static final WindowData WINDOW_DATA = new WindowData();

  @Implementation
  protected static long nativeCreate(String name, int cursorWindowSize) {
    return WINDOW_DATA.create(name, cursorWindowSize);
  }

  @Implementation
  protected static void nativeDispose(long windowPtr) {
    WINDOW_DATA.close(windowPtr);
  }

  @Implementation
  protected static byte[] nativeGetBlob(long windowPtr, int row, int column) {
    Value value = WINDOW_DATA.get(windowPtr).value(row, column);

    switch (value.type) {
      case Cursor.FIELD_TYPE_NULL:
        return null;
      case Cursor.FIELD_TYPE_BLOB:
        // This matches Android's behavior, which does not match the SQLite spec
        byte[] blob = (byte[]) value.value;
        return blob == null ? new byte[] {} : blob;
      case Cursor.FIELD_TYPE_STRING:
        // Matches the Android behavior to contain a zero-byte at the end
        byte[] stringBytes = ((String) value.value).getBytes(UTF_8);
        return Arrays.copyOf(stringBytes, stringBytes.length + 1);
      default:
        throw new android.database.sqlite.SQLiteException(
            "Getting blob when column is non-blob. Row " + row + ", col " + column);
    }
  }

  @Implementation
  protected static String nativeGetString(long windowPtr, int row, int column) {
    Value val = WINDOW_DATA.get(windowPtr).value(row, column);
    if (val.type == Cursor.FIELD_TYPE_BLOB) {
      throw new android.database.sqlite.SQLiteException(
          "Getting string when column is blob. Row " + row + ", col " + column);
    }
    Object value = val.value;
    return value == null ? null : String.valueOf(value);
  }

  @Implementation
  protected static long nativeGetLong(long windowPtr, int row, int column) {
    return nativeGetNumber(windowPtr, row, column).longValue();
  }

  @Implementation
  protected static double nativeGetDouble(long windowPtr, int row, int column) {
    return nativeGetNumber(windowPtr, row, column).doubleValue();
  }

  @Implementation
  protected static int nativeGetType(long windowPtr, int row, int column) {
    return WINDOW_DATA.get(windowPtr).value(row, column).type;
  }

  @Implementation
  protected static void nativeClear(long windowPtr) {
    WINDOW_DATA.clear(windowPtr);
  }

  @Implementation
  protected static int nativeGetNumRows(long windowPtr) {
    return WINDOW_DATA.get(windowPtr).numRows();
  }

  @Implementation
  protected static boolean nativePutBlob(long windowPtr, byte[] value, int row, int column) {
    // Real Android will crash in native code if putString is called with a null value.
    Objects.requireNonNull(value);
    return WINDOW_DATA
        .get(windowPtr)
        .putValue(new Value(value, Cursor.FIELD_TYPE_BLOB), row, column);
  }

  @Implementation
  protected static boolean nativePutString(long windowPtr, String value, int row, int column) {
    // Real Android will crash in native code if putString is called with a null value.
    Objects.requireNonNull(value);
    return WINDOW_DATA
        .get(windowPtr)
        .putValue(new Value(value, Cursor.FIELD_TYPE_STRING), row, column);
  }

  @Implementation
  protected static boolean nativePutLong(long windowPtr, long value, int row, int column) {
    return WINDOW_DATA
        .get(windowPtr)
        .putValue(new Value(value, Cursor.FIELD_TYPE_INTEGER), row, column);
  }

  @Implementation
  protected static boolean nativePutDouble(long windowPtr, double value, int row, int column) {
    return WINDOW_DATA
        .get(windowPtr)
        .putValue(new Value(value, Cursor.FIELD_TYPE_FLOAT), row, column);
  }

  @Implementation
  protected static boolean nativePutNull(long windowPtr, int row, int column) {
    return WINDOW_DATA
        .get(windowPtr)
        .putValue(new Value(null, Cursor.FIELD_TYPE_NULL), row, column);
  }

  @Implementation
  protected static boolean nativeAllocRow(long windowPtr) {
    return WINDOW_DATA.get(windowPtr).allocRow();
  }

  @Implementation
  protected static boolean nativeSetNumColumns(long windowPtr, int columnNum) {
    return WINDOW_DATA.get(windowPtr).setNumColumns(columnNum);
  }

  @Implementation
  protected static String nativeGetName(long windowPtr) {
    return WINDOW_DATA.get(windowPtr).getName();
  }

  protected static int setData(long windowPtr, SQLiteStatement stmt) throws SQLiteException {
    return WINDOW_DATA.setData(windowPtr, stmt);
  }

  private static Number nativeGetNumber(long windowPtr, int row, int column) {
    Value value = WINDOW_DATA.get(windowPtr).value(row, column);
    switch (value.type) {
      case Cursor.FIELD_TYPE_NULL:
      case SQLiteConstants.SQLITE_NULL:
        return 0;
      case Cursor.FIELD_TYPE_INTEGER:
      case Cursor.FIELD_TYPE_FLOAT:
        return (Number) value.value;
      case Cursor.FIELD_TYPE_STRING:
        {
          try {
            return Double.parseDouble((String) value.value);
          } catch (NumberFormatException e) {
            return 0;
          }
        }
      case Cursor.FIELD_TYPE_BLOB:
        throw new android.database.sqlite.SQLiteException("could not convert " + value);
      default:
        throw new android.database.sqlite.SQLiteException("unknown type: " + value.type);
    }
  }

  private static class Data {
    private final List<Row> rows;
    private final String name;
    private int numColumns;

    public Data(String name, int cursorWindowSize) {
      this.name = name;
      this.rows = new ArrayList<>();
    }

    public Value value(int rowN, int colN) {
      Row row = rows.get(rowN);
      if (row == null) {
        throw new IllegalArgumentException("Bad row number: " + rowN + ", count: " + rows.size());
      }
      return row.get(colN);
    }

    public int numRows() {
      return rows.size();
    }

    public boolean putValue(Value value, int rowN, int colN) {
      return rows.get(rowN).set(colN, value);
    }

    public void fillWith(SQLiteStatement stmt) throws SQLiteException {
      // Android caches results in the WindowedCursor to allow moveToPrevious() to function.
      // Robolectric will have to cache the results too. In the rows list.
      while (stmt.step()) {
        rows.add(fillRowValues(stmt));
      }
    }

    private static int cursorValueType(final int sqliteType) {
      switch (sqliteType) {
        case SQLiteConstants.SQLITE_NULL:
          return Cursor.FIELD_TYPE_NULL;
        case SQLiteConstants.SQLITE_INTEGER:
          return Cursor.FIELD_TYPE_INTEGER;
        case SQLiteConstants.SQLITE_FLOAT:
          return Cursor.FIELD_TYPE_FLOAT;
        case SQLiteConstants.SQLITE_TEXT:
          return Cursor.FIELD_TYPE_STRING;
        case SQLiteConstants.SQLITE_BLOB:
          return Cursor.FIELD_TYPE_BLOB;
        default:
          throw new IllegalArgumentException(
              "Bad SQLite type " + sqliteType + ". See possible values in SQLiteConstants.");
      }
    }

    private static Row fillRowValues(SQLiteStatement stmt) throws SQLiteException {
      final int columnCount = stmt.columnCount();
      Row row = new Row(columnCount);
      for (int index = 0; index < columnCount; index++) {
        row.set(index, new Value(stmt.columnValue(index), cursorValueType(stmt.columnType(index))));
      }
      return row;
    }

    public void clear() {
      rows.clear();
    }

    public boolean allocRow() {
      rows.add(new Row(numColumns));
      return true;
    }

    public boolean setNumColumns(int numColumns) {
      this.numColumns = numColumns;
      return true;
    }

    public String getName() {
      return name;
    }
  }

  private static class Row {
    private final List<Value> values;

    public Row(int length) {
      values = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        values.add(new Value(null, Cursor.FIELD_TYPE_NULL));
      }
    }

    public Value get(int n) {
      return values.get(n);
    }

    public boolean set(int colN, Value value) {
      values.set(colN, value);
      return true;
    }
  }

  private static class Value {
    private final Object value;
    private final int type;

    public Value(final Object value, final int type) {
      this.value = value;
      this.type = type;
    }
  }

  private static class WindowData {
    private final AtomicLong windowPtrCounter = new AtomicLong(0);
    private final Map<Number, Data> dataMap = new ConcurrentHashMap<>();

    public Data get(long ptr) {
      Data data = dataMap.get(ptr);
      if (data == null) {
        throw new IllegalArgumentException(
            "Invalid window pointer: " + ptr + "; current pointers: " + dataMap.keySet());
      }
      return data;
    }

    public int setData(final long ptr, final SQLiteStatement stmt) throws SQLiteException {
      Data data = get(ptr);
      data.fillWith(stmt);
      return data.numRows();
    }

    public void close(final long ptr) {
      Data removed = dataMap.remove(ptr);
      if (removed == null) {
        throw new IllegalArgumentException(
            "Bad cursor window pointer " + ptr + ". Valid pointers: " + dataMap.keySet());
      }
    }

    public void clear(final long ptr) {
      get(ptr).clear();
    }

    public long create(String name, int cursorWindowSize) {
      long ptr = windowPtrCounter.incrementAndGet();
      dataMap.put(ptr, new Data(name, cursorWindowSize));
      return ptr;
    }
  }

  // TODO: Implement these methods
  // private static native int nativeCreateFromParcel(Parcel parcel);
  // private static native void nativeWriteToParcel($ptrClass windowPtr, Parcel parcel);
  // private static native void nativeFreeLastRow($ptrClass windowPtr);
  // private static native void nativeCopyStringToBuffer($ptrClass windowPtr, int row, int column,
  // CharArrayBuffer buffer);
}

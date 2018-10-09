package com.csforge.sstable;

import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.ColumnDefinition;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.cql3.ResultSet;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.LongType;
import org.apache.cassandra.db.marshal.TimestampType;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

public class TableTransformer {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    public boolean rawTimestamps = false;

    public TableTransformer() {
    }

    public String colValue(ResultSet results, List<ByteBuffer> row, int i) throws Exception {
        ByteBuffer v = row.get(i).duplicate();
        String ret = "null";
        if (v != null) {
            EnumSet<ResultSet.Flag> flags = (EnumSet<ResultSet.Flag>) CassandraUtils.readPrivate(results.metadata, "flags");
            if (flags.contains(ResultSet.Flag.NO_METADATA)) {
                ret = "0x" + ByteBufferUtil.bytesToHex(v);
            } else {
                AbstractType<?> type = results.metadata.names.get(i).type;
                if (type.isCollection()) {
                    ret = type.getSerializer().deserialize(v).toString();
                } else if (type.isValueCompatibleWith(TimestampType.instance) && rawTimestamps) {
                    ret = LongType.instance.getSerializer().deserialize(v).toString();
                } else {
                    ret = type.getString(v);
                }
            }
        }
        return ret;
    }

    private static void printLine(char left, char mid, char right, char cross, int[] padding, PrintStream out) {
        out.print(" " + left);
        for (int i = 0; i < padding.length; i++) {
            out.print(StringUtils.repeat(mid, padding[i]) + ((i == (padding.length - 1)) ? right : cross));
        }
    }

    public void dumpResultsAsCsv(CFMetaData cfm, ResultSet results, PrintStream out, boolean flushAfterLine)
            throws Exception {
        int numCols =results.metadata.names.size();
        ArrayList<String> colData = new ArrayList<>(numCols);

        CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT);
        for (int row = 0; row < results.rows.size(); row++) {
            List<ByteBuffer> rowData = results.rows.get(row);

            colData.clear();
            for (int col = 0; col < numCols; col++) {
                colData.add(colValue(results, rowData, col));
            }

            printer.printRecord(colData);
            if (flushAfterLine)
                printer.flush();
        }
    }

    public void dumpResults(CFMetaData cfm, ResultSet results, PrintStream out) throws Exception {
        if(results.rows.isEmpty()) return; // empty
        // find spacing
        int[] padding = new int[results.rows.get(0).size()];
        for (int i = 0; i < results.rows.get(0).size(); i++) {
            padding[i] = 3 + results.metadata.names.get(i).name.toString().length();
        }
        for (List<ByteBuffer> row : results.rows) {
            for (int i = 0; i < row.size(); i++) {
                padding[i] = Math.max(padding[i], colValue(results, row, i).length());
            }
        }

        // headers
        out.print(ANSI_WHITE);
        printLine('┌', '─', '┐', '┬', padding, out);
        out.println();
        out.print(" ");
        for (int i = 0; i < results.metadata.names.size(); i++) {
            ColumnSpecification spec = results.metadata.names.get(i);
            out.print(ANSI_WHITE + "│" + ANSI_RESET);

            ColumnDefinition def = cfm.getColumnDefinition(spec.name);
            if (def != null && def.isPartitionKey()) {
                out.print(ANSI_RED);
            } else if (def != null && def.isClusteringColumn()) {
                out.print(ANSI_CYAN);
            }
            out.print(String.format("%-" + padding[i] + "s", spec.name));
            out.print(ANSI_RESET);
        }
        out.println(ANSI_WHITE + "│");
        printLine('╞', '═', '╡', '╪', padding, out);
        out.println(ANSI_RESET);
        out.print(" ");

        // data
        for (int r = 0; r < results.rows.size(); r++) {
            if (r > 0) {
                out.print(" ");
            }
            List<ByteBuffer> row = results.rows.get(r);
            for (int i = 0; i < row.size(); i++) {
                out.print(ANSI_WHITE + "│" + ANSI_RESET);
                out.print(String.format("%-" + padding[i] + "s", colValue(results, row, i)));
            }

            out.println(ANSI_WHITE + "│");
            if (r == results.rows.size() - 1) {
                printLine('└', '─', '┘', '┴', padding, out);
            } else {
                printLine('├', '─', '┤', '┼', padding, out);
            }
            out.println(ANSI_RESET);
        }
        out.flush();
    }
}

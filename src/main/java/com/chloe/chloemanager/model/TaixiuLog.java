package com.chloe.chloemanager.model;

import com.chloe.chloemanager.ChloeManagerPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

/**
 * MODEL — TaixiuLog
 *
 * Handles persistent read/write for the Tài Xỉu game log file.
 *
 * File location: plugins/ChloeManager/taixiu_log.txt
 *
 * Format:
 *   Line 1:  ROUND=<number>
 *   Line 2+: [timestamp] Phiên <N> | Xúc xắc: [d1-d2-d3] Tổng: <sum> | Kết quả: <TAI/XIU/TRIPLE> | Người thắng: <name> cược $<amount> thắng $<payout>
 *
 * The round counter persists across server restarts.
 */
public class TaixiuLog {

    private static final String FILE_NAME   = "taixiu_log.txt";
    private static final String ROUND_KEY   = "ROUND=";
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    /** After this many rounds, the log file is wiped and counting restarts at 1. */
    private static final int MAX_ROUNDS_BEFORE_RESET = 200;

    private final File    logFile;
    private       int     currentRound = 1;

    public TaixiuLog(ChloeManagerPlugin plugin) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        logFile = new File(dataFolder, FILE_NAME);
        load();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the current phiên number (loaded from file on startup). */
    public int getCurrentRound() {
        return currentRound;
    }

    /**
     * Increments the round counter (call at the start of each new round).
     * After 200 rounds, the log file is wiped and counting restarts at 1.
     */
    public void incrementRound() {
        currentRound++;
        if (currentRound > MAX_ROUNDS_BEFORE_RESET) {
            resetLog();
        } else {
            updateRoundCounter();
        }
    }

    /**
     * Wipes the log file and resets the round counter to 1.
     */
    private void resetLog() {
        currentRound = 1;
        writeRoundCounter(1);
        ChloeManagerPlugin.getInstance().getLogger()
                .info("Taixiu log reached 200 rounds — reset to 1.");
    }

    /**
     * Appends a winner entry to the log.
     *
     * @param round      The phiên number.
     * @param diceStr    e.g. "[ 3 - 5 - 6 ]"
     * @param sum        Total dice sum.
     * @param resultName "Tài", "Xỉu", or "TRIPLE (Nhà cái)"
     * @param winnerName Player name, or null if triple (no winner).
     * @param betAmount  How much the winner bet.
     * @param payout     How much they received back (0 on triple).
     */
    public void appendResult(int round, String diceStr, int sum, String resultName,
                             String winnerName, double betAmount, double payout) {
        String timestamp = SDF.format(new Date());
        String winInfo   = winnerName != null
                ? "Người thắng: " + winnerName + " cược $" + fmt(betAmount) + " thắng $" + fmt(payout)
                : "Nhà cái thắng tất cả — tổng cược: $" + fmt(betAmount);

        String line = "[" + timestamp + "] Phiên " + round
                + " | Xúc xắc: " + diceStr + " Tổng: " + sum
                + " | Kết quả: " + resultName
                + " | " + winInfo;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(logFile, true), StandardCharsets.UTF_8))) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            ChloeManagerPlugin.getInstance().getLogger()
                    .log(Level.WARNING, "Failed to write taixiu_log.txt", e);
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /**
     * Reads the first line of the log to find the current round number.
     * If the file doesn't exist yet, starts from round 1.
     */
    private void load() {
        if (!logFile.exists()) {
            currentRound = 1;
            writeRoundCounter(1);
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(logFile), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            if (firstLine != null && firstLine.startsWith(ROUND_KEY)) {
                currentRound = Integer.parseInt(firstLine.substring(ROUND_KEY.length()).trim());
            } else {
                // Legacy file with no counter — count the existing lines
                currentRound = 1;
            }
        } catch (IOException | NumberFormatException e) {
            ChloeManagerPlugin.getInstance().getLogger()
                    .warning("Could not read round counter from taixiu_log.txt, starting from 1.");
            currentRound = 1;
        }
    }

    /**
     * Rewrites the FIRST line of the file to update the round counter,
     * preserving all subsequent log entries.
     */
    private void updateRoundCounter() {
        // Read everything after line 1
        StringBuilder rest = new StringBuilder();
        if (logFile.exists()) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(logFile), StandardCharsets.UTF_8))) {
                reader.readLine(); // skip old first line
                String line;
                while ((line = reader.readLine()) != null) {
                    rest.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                ChloeManagerPlugin.getInstance().getLogger()
                        .log(Level.WARNING, "Failed to read taixiu_log.txt for update", e);
            }
        }

        // Rewrite with updated counter
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(logFile, false), StandardCharsets.UTF_8))) {
            writer.write(ROUND_KEY + currentRound);
            writer.newLine();
            writer.write(rest.toString());
        } catch (IOException e) {
            ChloeManagerPlugin.getInstance().getLogger()
                    .log(Level.WARNING, "Failed to update round counter in taixiu_log.txt", e);
        }
    }

    /** Writes a brand-new file with just the round counter line. */
    private void writeRoundCounter(int round) {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(logFile, false), StandardCharsets.UTF_8))) {
            writer.write(ROUND_KEY + round);
            writer.newLine();
        } catch (IOException e) {
            ChloeManagerPlugin.getInstance().getLogger()
                    .log(Level.WARNING, "Failed to create taixiu_log.txt", e);
        }
    }

    private String fmt(double v) {
        return String.format("%,.2f", v);
    }
}

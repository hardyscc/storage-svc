package com.storagesvc.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Utility class to handle AWS S3 chunked transfer encoding.
 * AWS S3 uses chunked transfer encoding with the following format:
 * 
 * chunk-size;chunk-signature=signature
 * chunk-data
 * 0;chunk-signature=final-signature
 * 
 * This class parses and extracts only the actual data content.
 */
public class ChunkedTransferDecoder {

    /**
     * Decodes an AWS S3 chunked transfer encoded input stream.
     * 
     * @param inputStream The chunked input stream
     * @return A new InputStream containing only the actual data
     * @throws IOException if there's an error reading the stream
     */
    public static InputStream decode(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        while (true) {
            // Read chunk size line
            String chunkSizeLine = readLine(inputStream);
            if (chunkSizeLine == null || chunkSizeLine.trim().isEmpty()) {
                break;
            }

            // Parse chunk size (before semicolon if present)
            int semicolonIndex = chunkSizeLine.indexOf(';');
            String chunkSizeStr = semicolonIndex >= 0 ? chunkSizeLine.substring(0, semicolonIndex) : chunkSizeLine;

            int chunkSize;
            try {
                chunkSize = Integer.parseInt(chunkSizeStr.trim(), 16); // Hex format
            } catch (NumberFormatException e) {
                throw new IOException("Invalid chunk size: " + chunkSizeStr, e);
            }

            // If chunk size is 0, we've reached the end
            if (chunkSize == 0) {
                break;
            }

            // Read the actual chunk data
            byte[] chunkData = new byte[chunkSize];
            int totalRead = 0;
            while (totalRead < chunkSize) {
                int bytesRead = inputStream.read(chunkData, totalRead, chunkSize - totalRead);
                if (bytesRead == -1) {
                    throw new IOException("Unexpected end of stream while reading chunk data");
                }
                totalRead += bytesRead;
            }

            // Write chunk data to buffer
            buffer.write(chunkData);

            // Read the trailing CRLF after chunk data
            readLine(inputStream);
        }

        return new java.io.ByteArrayInputStream(buffer.toByteArray());
    }

    /**
     * Checks if the input stream appears to be using AWS S3 chunked transfer encoding.
     * This is a heuristic check looking for hex chunk sizes followed by semicolons.
     * 
     * @param inputStream The input stream to check
     * @return true if it appears to be chunked, false otherwise
     * @throws IOException if there's an error reading the stream
     */
    public static boolean isChunkedTransferEncoding(InputStream inputStream) throws IOException {
        // Mark the stream so we can reset it
        if (!inputStream.markSupported()) {
            return false; // Can't check without mark support
        }

        inputStream.mark(100); // Mark first 100 bytes

        try {
            // Read the first line
            String firstLine = readLine(inputStream);
            if (firstLine == null) {
                return false;
            }

            // Check if it looks like a chunk size line (hex number followed by semicolon)
            int semicolonIndex = firstLine.indexOf(';');
            if (semicolonIndex > 0) {
                String possibleHex = firstLine.substring(0, semicolonIndex).trim();
                try {
                    Integer.parseInt(possibleHex, 16);
                    return true; // Successfully parsed as hex
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            return false;
        } finally {
            inputStream.reset(); // Reset to original position
        }
    }

    /**
     * Reads a line from the input stream (until CRLF or LF).
     * 
     * @param inputStream The input stream to read from
     * @return The line as a string, or null if end of stream
     * @throws IOException if there's an error reading
     */
    private static String readLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
        int b;
        boolean foundCR = false;

        while ((b = inputStream.read()) != -1) {
            if (b == '\r') {
                foundCR = true;
            } else if (b == '\n') {
                break; // End of line
            } else {
                if (foundCR) {
                    // CR without LF, add the CR back
                    lineBuffer.write('\r');
                    foundCR = false;
                }
                lineBuffer.write(b);
            }
        }

        if (lineBuffer.size() == 0 && b == -1) {
            return null; // End of stream
        }

        return lineBuffer.toString(StandardCharsets.UTF_8);
    }
}

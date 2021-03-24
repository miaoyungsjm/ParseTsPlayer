package com.excellence.ggz.libparsetsstream.Section;

import com.excellence.ggz.libparsetsstream.Packet.Packet;
import com.excellence.ggz.libparsetsstream.Section.entity.Section;

import static com.excellence.ggz.libparsetsstream.Packet.PacketManager.PACKET_LENGTH_204;

/**
 * @author ggz
 * @date 2021/3/22
 */
public abstract class AbstractSectionManager {
    private static final int AF_STATUS_PAYLOAD_ONLY = 0x01;
    private static final int SECTION_HEADER_LENGTH = 3;
    private static final int PAYLOAD_POINTER_FIELD = 1;
    private static final int CRC_16 = 16;
    private static final int CONTINUITY_COUNTER_MAXIMUM = 0xF;

    private Section mSection = null;
    private int mNextContinuityCounter = -1;

    public void assembleSection(int inputTableId, Packet packet) {
        int packetLength = packet.getPacketLength();
        int adaptationFieldControl = packet.getAdaptationFieldControl();
        int payloadUnitStartIndicator = packet.getPayloadUnitStartIndicator();
        int continuityCounter = packet.getContinuityCounter();
        byte[] payLoad = packet.getPayLoad();

        if (adaptationFieldControl == AF_STATUS_PAYLOAD_ONLY) {
            if (payloadUnitStartIndicator == 1) {
                // payload_unit_start_indicator == 1，packet carries the first byte of a PSI section
                int tableId = payLoad[PAYLOAD_POINTER_FIELD] & 0xFF;
                int sectionSyntaxIndicator = (payLoad[PAYLOAD_POINTER_FIELD + 1] >> 7) & 0x1;
                int zero = (payLoad[PAYLOAD_POINTER_FIELD + 1] >> 6) & 0x1;
                int reserved = payLoad[PAYLOAD_POINTER_FIELD + 1] & 0x11;
                int sectionLength = ((payLoad[PAYLOAD_POINTER_FIELD + 1] & 0x3) << 8 |
                        payLoad[PAYLOAD_POINTER_FIELD + 2] & 0xFF) & 0x3FF;
                byte[] buff = new byte[sectionLength];

                if (tableId == inputTableId) {
                    // the maximum value of section length in one packet
                    int effectiveSectionLength;
                    int payloadLength = payLoad.length;
                    if (packetLength == PACKET_LENGTH_204) {
                        effectiveSectionLength = payloadLength - PAYLOAD_POINTER_FIELD
                                - SECTION_HEADER_LENGTH - CRC_16;
                    } else {
                        effectiveSectionLength = payloadLength - PAYLOAD_POINTER_FIELD
                                - SECTION_HEADER_LENGTH;
                    }

                    int remainLength;
                    int sectionStartPos = PAYLOAD_POINTER_FIELD + SECTION_HEADER_LENGTH;
                    if (sectionLength > effectiveSectionLength) {
                        // incomplete
                        System.arraycopy(payLoad, sectionStartPos, buff, 0, effectiveSectionLength);
                        remainLength = sectionLength - effectiveSectionLength;
                    } else {
                        // complete
                        System.arraycopy(payLoad, sectionStartPos, buff, 0, sectionLength);
                        remainLength = 0;
                    }

                    mNextContinuityCounter = continuityCounter + 1;
                    if (mNextContinuityCounter > CONTINUITY_COUNTER_MAXIMUM) {
                        mNextContinuityCounter = 0;
                    }
                    mSection = new Section(tableId, sectionSyntaxIndicator, zero, reserved,
                            sectionLength, buff, remainLength);
                }
            } else {
                if (mSection == null || continuityCounter != mNextContinuityCounter) {
                    return;
                }

                int sectionLength = mSection.getSectionLength();
                int remainLength = mSection.getRemainLength();
                byte[] sectionBuff = mSection.getSectionBuff();

                // the maximum value of section length in one packet
                int effectiveSectionLength;
                int payloadLength = payLoad.length;
                if (packetLength == PACKET_LENGTH_204) {
                    effectiveSectionLength = payloadLength - CRC_16;
                } else {
                    effectiveSectionLength = payloadLength;
                }

                int buffStartPos = sectionLength - remainLength;
                if (remainLength > effectiveSectionLength) {
                    // incomplete
                    System.arraycopy(payLoad, 0, sectionBuff, buffStartPos, effectiveSectionLength);
                    remainLength = remainLength - effectiveSectionLength;
                } else {
                    // complete
                    System.arraycopy(payLoad, 0, sectionBuff, buffStartPos, remainLength);
                    remainLength = 0;
                }

                mNextContinuityCounter = continuityCounter + 1;
                if (mNextContinuityCounter > CONTINUITY_COUNTER_MAXIMUM) {
                    mNextContinuityCounter = 0;
                }
                mSection.setRemainLength(remainLength);
            }

            if (mSection != null) {
                if (mSection.getRemainLength() == 0) {
                    parseSection(mSection);
                }
            }
        } else {
            // todo: adaptation_field()
        }
    }

    public abstract void parseSection(Section section);
}

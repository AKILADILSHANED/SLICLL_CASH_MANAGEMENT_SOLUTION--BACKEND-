package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Service;

import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.APIResponse.customAPIResponse;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.*;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Entity.transferChannel;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Logs.LogActivity;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.UserRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.transferChannelRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.channelDetailsMapper;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.getChannelsForDefineOptionsMapper;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.searchChannelForRemoveMapper;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.setPriorityLevelMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class transferChannelIMPL implements transferChannelService {
    @Autowired
    transferChannelRepo transferChannelRepository;
    @Autowired
    HttpSession session;
    @Autowired
    UserRepo userRepository;
    @Autowired
    JdbcTemplate template;

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> addChannel(addNewChannelDTO newChannel) {
        try {
            String newChannelId;
            //Get last Channel ID from the table;
            String lastChannelId = transferChannelRepository.getLastChannelId();
            if (lastChannelId == null) {
                newChannelId = "CHNL-01";
            } else {
                int numericPart = Integer.parseInt(lastChannelId.substring(5, 7)) + 1;
                newChannelId = "CHNL-" + String.format("%02d", numericPart);
            }

            //Save new channel details to the Transfer Channel table;
            transferChannelRepository.save(
                    new transferChannel(
                            newChannelId,
                            newChannel.getChannelType(),
                            newChannel.getShortKey(),
                            newChannel.getPriorityLevel(),
                            LocalDateTime.now(),
                            0,
                            null,
                            userRepository.findById(session.getAttribute("userId").toString()).get()
                    )
            );
            return ResponseEntity.status(HttpStatus.OK).body(
                    new customAPIResponse<>(
                            true,
                            "Transfer Channel registered successfully with Channel ID: " + newChannelId,
                            null
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.OK).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while registering the channel. Please contact administrator!",
                            null
                    )
            );
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<channelDetailsDTO>>> channelDetails() {
        try {
            String Sql = "SELECT CHNL.channel_id, CHNL.channel_type, CHNL.short_key, CHNL.priority_level, CHNL.created_date, CASE WHEN CHNL.deleted_status = 0 THEN 'Active' ELSE 'Deleted' END AS 'deleted_status', CASE WHEN CHNL.delete_by IS NULL THEN 'N/A' ELSE USRDELETED.user_first_name END AS 'delete_by', USRDEFINED.user_first_name FROM transfer_channel CHNL LEFT JOIN user USRDEFINED ON CHNL.defined_by = USRDEFINED.user_id LEFT JOIN user USRDELETED ON CHNL.delete_by = USRDELETED.user_id ORDER BY CHNL.priority_level ASC";
            List<channelDetailsDTO> channelDetails = template.query(Sql, new channelDetailsMapper());
            if (channelDetails.isEmpty()) {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                false,
                                "No Transfer Channels are available!",
                                null
                        )
                );
            } else {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                null,
                                channelDetails
                        )
                );
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.OK).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while fetching channel data. Please contact administrator!",
                            null
                    )
            );
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> removeChannel(String channelId) {
        try {
            //Check whether any record is available for provided channel id;
            Optional<transferChannel> channel = transferChannelRepository.findById(channelId);
            if (channel.isEmpty()) {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                false,
                                "No Transfer Channel is identified for provided Channel ID!",
                                null
                        )
                );
            } else {
                int affectedRow = transferChannelRepository.removeChannel(session.getAttribute("userId").toString(), channelId);
                if (affectedRow > 0) {
                    //Check whether the record is already removed or not;
                    if (channel.get().getDeleteStatus() == 1) {
                        return ResponseEntity.status(HttpStatus.OK).body(
                                new customAPIResponse<>(
                                        false,
                                        "This Channel ID: " + channelId + " is already deleted. No further deletion is required!",
                                        null
                                )
                        );
                    } else {
                        return ResponseEntity.status(HttpStatus.OK).body(
                                new customAPIResponse<>(
                                        true,
                                        "Channel ID: " + channelId + " removed successfully!",
                                        null
                                )
                        );
                    }
                } else {
                    //No code block to be run;
                    return null;
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.OK).body(
                    new customAPIResponse<>(
                            false,
                            e.getMessage() + " Un-expected error occurred while removing channel data. Please contact administrator!",
                            null
                    )
            );
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<channelSearchForRemoveDTO>> searchChannelForRemove(String channelId) {
        try {
            //Check whether any record is available for provided channel id;
            Optional<transferChannel> channel = transferChannelRepository.findById(channelId);

            if (channel.isEmpty()) {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                false,
                                "No Transfer Channel is identified for provided Channel ID!",
                                null
                        )
                );
            } else {
                String Sql = "SELECT CHNL.channel_id, CHNL.channel_type, CHNL.short_key, CHNL.priority_level, CHNL.created_date, USRDEFINED.user_first_name FROM transfer_channel CHNL LEFT JOIN user USRDEFINED ON CHNL.defined_by = USRDEFINED.user_id WHERE CHNL.channel_id = ?";
                List<channelSearchForRemoveDTO> searchedChannel = template.query(Sql, new Object[]{channelId}, new searchChannelForRemoveMapper());
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                null,
                                searchedChannel.get(0)
                        )
                );
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.OK).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while fetching data. Please contact administrator!",
                            null
                    )
            );
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<setPriorityLevelDTO>>> setPriorityLevel() {
        try {
            String Sql = "SELECT channel_id, channel_type, short_key, priority_level FROM transfer_channel WHERE deleted_status = 0 ORDER BY priority_level ASC";
            List<setPriorityLevelDTO> priorityLevelList = template.query(Sql, new setPriorityLevelMapper());
            if (priorityLevelList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                false,
                                "No Transfer Channels available",
                                null
                        )
                );
            } else {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                null,
                                priorityLevelList
                        )
                );
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.OK).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while fetching Channel Details. Please contact administrator!",
                            null
                    )
            );
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> changePriorityLevel(String channelId, int newLevel) {
        try {
            int affectedRows = transferChannelRepository.updateNewLevel(newLevel, channelId);
            if (affectedRows > 0) {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                "Successfully set level for Channel ID: " + channelId,
                                null
                        )
                );
            } else {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                false,
                                "Un-expected error occurred while setting new level. Please contact administrator!",
                                null
                        )
                );
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.OK).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while setting new level. Please contact administrator!",
                            null
                    )
            );
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<getChannelsForDefineOptionsDTO>>> getChannelsForDefineOptions() {
        try {
            String Sql = "SELECT channel_id, channel_type, short_key FROM transfer_channel WHERE deleted_status = 0;";
            List<getChannelsForDefineOptionsDTO> channelList = template.query(Sql, new getChannelsForDefineOptionsMapper());
            if (channelList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                false,
                                "No Channel Details available!",
                                null
                        )
                );
            } else {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                null,
                                channelList
                        )
                );
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.OK).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while fetching channel details. Please contact administrator!",
                            null
                    )
            );
        }
    }

    @Override
    @LogActivity(methodDescription = "This method will fetch active transfer chanel list")
    public ResponseEntity<customAPIResponse<List<transferChanelForTransferHistory>>> getTransferChanel() {
        List<transferChanelForTransferHistory> chanelList = transferChannelRepository.chanelList();
        if (!chanelList.isEmpty()) {
            return ResponseEntity.status(HttpStatus.OK).body(
                    new customAPIResponse<List<transferChanelForTransferHistory>>(
                            true,
                            null,
                            chanelList
                    )
            );
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new customAPIResponse<>(
                            false,
                            "No Transfer Chanel details to fetch",
                            null
                    )
            );
        }
    }
}

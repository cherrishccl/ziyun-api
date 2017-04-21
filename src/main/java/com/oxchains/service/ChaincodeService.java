package com.oxchains.service;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.oxchains.bean.model.Customer;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.common.Configtx;
import org.hyperledger.fabric.protos.msp.Identities;
import org.hyperledger.fabric.protos.peer.FabricTransaction;
import org.hyperledger.fabric.protos.peer.Query;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.ChaincodeEndorsementPolicyParseException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.exception.TransactionException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * ChaincodeService
 *
 * @author liuruichao
 * Created on 2017/4/6 16:23
 */
@Service
@Slf4j
public class ChaincodeService extends BaseService implements InitializingBean, DisposableBean {
    @Value("${chaincode.name}")
    private String CHAIN_CODE_NAME;

    @Value("${chaincode.path}")
    private String CHAIN_CODE_PATH;

    @Value("${chaincode.version}")
    private String CHAIN_CODE_VERSION;

    @Value("${chaincode.resource.path}")
    private String TEST_FIXTURES_PATH;

    @Value("${chaincode.ca.url}")
    private String CA_URL;

    @Value("${chaincode.orderer.url}")
    private String ORDERER_URL;

    @Value("${chaincode.peer.address.list}")
    private String PEER_LIST;

    @Value("${chain.config.path}")
    private String configPath;

    @Value("${chain.name}")
    private String chainName;

    private Chain chain;

    private HFClient hfClient;

    private HFCAClient hfcaClient;

    private ChainCodeID chainCodeID;

    public void installChaincode() throws InvalidArgumentException, ProposalException {
        InstallProposalRequest installProposalRequest = hfClient.newInstallProposalRequest();
        installProposalRequest.setChaincodeID(chainCodeID);
        installProposalRequest.setChaincodeSourceLocation(new File(TEST_FIXTURES_PATH));
        installProposalRequest.setChaincodeVersion(CHAIN_CODE_VERSION);

        Collection<ProposalResponse> responses = chain.sendInstallProposal(installProposalRequest, chain.getPeers());
        for (ProposalResponse response : responses) {
            if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                System.out.println(String.format("Successful install proposal response Txid: %s from peer %s",
                        response.getTransactionID(),
                        response.getPeer().getName()));
            } else {
                System.out.println("install chaincode error!");
            }
        }
    }

    public void instantiateChaincode() throws IOException, ProposalException, InvalidArgumentException, InterruptedException, ExecutionException, TimeoutException, ChaincodeEndorsementPolicyParseException {
        ChainCodeID chainCodeID = ChainCodeID.newBuilder().setName(CHAIN_CODE_NAME)
                .setVersion(CHAIN_CODE_VERSION)
                .setPath(CHAIN_CODE_PATH).build();
        InstantiateProposalRequest instantiateProposalRequest = hfClient.newInstantiationProposalRequest();
        instantiateProposalRequest.setChaincodeID(chainCodeID);
        instantiateProposalRequest.setFcn("init");
        instantiateProposalRequest.setArgs(new String[]{});

        ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
        // 背书策略
        chaincodeEndorsementPolicy.fromFile(new File(TEST_FIXTURES_PATH + "/members_from_org1_or_2.policy"));
        //chaincodeEndorsementPolicy.fromYamlFile(new File(TEST_FIXTURES_PATH + "/chaincodeendorsementpolicy.yaml"));
        instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);

        Collection<ProposalResponse> successful = new ArrayList<>();
        // Send instantiate transaction to peers
        Collection<ProposalResponse> responses = chain.sendInstantiationProposal(instantiateProposalRequest, chain.getPeers());
        if (responses != null && responses.size() > 0) {
            for (ProposalResponse response : responses) {
                if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    successful.add(response);
                    log.info(String.format("Succesful instantiate proposal response Txid: %s from peer %s",
                            response.getTransactionID(),
                            response.getPeer().getName()));
                } else {
                    System.out.println("Instantiate Chaincode error! " + response.getMessage());
                }
            }

            /// Send instantiate transaction to orderer
            chain.sendTransaction(successful, chain.getOrderers());
            System.out.println("instantiateChaincode done");
        }
    }

    public Set<String> getChannels() throws ProposalException, InvalidArgumentException {
        Set<String> channels = hfClient.queryChannels(chain.getPeers().iterator().next());
        return channels;
    }

    public List<Query.ChaincodeInfo> getInstalledChaincodes() throws ProposalException, InvalidArgumentException {
        List<Query.ChaincodeInfo> chaincodeInfos = hfClient.queryInstalledChaincodes(chain.getPeers().iterator().next());
        for (Query.ChaincodeInfo chaincodeInfo : chaincodeInfos) {
            System.out.println(chaincodeInfo.getName());
        }
        return chaincodeInfos;
    }

    public Chain getChain(String chainName, Orderer orderer) throws InvalidArgumentException, TransactionException {
        Chain chain = hfClient.newChain(chainName);

        Set<Peer> peers = getPeers();
        for (Peer peer : peers) {
            chain.addPeer(peer);
        }

        chain.addOrderer(orderer);
        chain.initialize();
        return chain;
    }

    public Chain createChain(String configPath, Orderer orderer, String chainName) throws IOException, InvalidArgumentException, TransactionException, ProposalException {
        ChainConfiguration chainConfiguration = new ChainConfiguration(new File(configPath));
        Chain newChain = hfClient.newChain(chainName, orderer, chainConfiguration);
        newChain.setTransactionWaitTime(100000);
        newChain.setDeployWaitTime(120000);

        Set<Peer> peers = getPeers();
        for (Peer peer : peers) {
            log.info("join chain: " + newChain.joinPeer(peer));
        }

        newChain.initialize();
        return newChain;
    }

    private Set<Peer> getPeers() throws InvalidArgumentException {
        Set<Peer> peers = new HashSet<>();
        String[] peerAddressList = PEER_LIST.split(",");
        for (String address : peerAddressList) {
            String[] params = address.split("@");
            peers.add(hfClient.newPeer(params[0], params[1]));
        }
        return peers;
    }

    public String invoke(String func, String[] args) throws InvalidArgumentException, ProposalException, InterruptedException, ExecutionException, TimeoutException {
        String txID = null;

        TransactionProposalRequest transactionProposalRequest = hfClient.newTransactionProposalRequest();
        transactionProposalRequest.setChaincodeID(chainCodeID);
        transactionProposalRequest.setFcn(func);
        transactionProposalRequest.setArgs(args);

        // send Proposal to peers
        Collection<ProposalResponse> transactionPropResp = chain.sendTransactionProposal(transactionProposalRequest, chain.getPeers());

        // send Proposal to orderers
        Collection<ProposalResponse> successful = new ArrayList<>();
        for (ProposalResponse response : transactionPropResp) {
            if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                txID = response.getTransactionID();
                successful.add(response);
            }
        }
        chain.sendTransaction(successful, chain.getOrderers());

        return txID;
    }

    public String query(String func, String[] args) {
        QueryByChaincodeRequest queryByChaincodeRequest = hfClient.newQueryProposalRequest();
        queryByChaincodeRequest.setArgs(args);
        queryByChaincodeRequest.setFcn(func);
        queryByChaincodeRequest.setChaincodeID(chainCodeID);

        Collection<ProposalResponse> queryProposals = null;
        try {
            queryProposals = chain.queryByChaincode(queryByChaincodeRequest, chain.getPeers());
        } catch (InvalidArgumentException | ProposalException ignored) {
            return null;
        }
        for (ProposalResponse proposalResponse : queryProposals) {
            if (proposalResponse.isVerified() && proposalResponse.getStatus() == ProposalResponse.Status.SUCCESS) {
                String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
                return payload;
            }
        }

        return null;
    }

    public BlockchainInfo queryChain() throws InvalidArgumentException, ProposalException, InvalidProtocolBufferException {
        BlockchainInfo blockchainInfo = chain.queryBlockchainInfo();
        /*String chainCurrentHash = Hex.encodeHexString(blockchainInfo.getCurrentBlockHash());
        String chainPreviousHash = Hex.encodeHexString(blockchainInfo.getPreviousBlockHash());*/

        /*System.out.println("height: " + blockchainInfo.getHeight());
        System.out.println("currentHash: " + chainCurrentHash);
        System.out.println("previousHash: " + chainPreviousHash);*/

        System.out.println("size: " + blockchainInfo.getBlockchainInfo().getSerializedSize());
       // TODO test
        for (int i = 0; i < blockchainInfo.getHeight(); i++) {
            BlockInfo blockInfo = queryBlock(i);
            // block header
            Common.BlockHeader blockHeader = blockInfo.getBlock().getHeader();
            /*System.out.printf("dataHash: %s, previousHash: %s.\n",
                    Hex.encodeHexString(blockHeader.getDataHash().toByteArray()),
                    Hex.encodeHexString(blockHeader.getPreviousHash().toByteArray()));*/
            Common.BlockMetadata blockMetadata = blockInfo.getBlock().getMetadata();
            /*for (ByteString str : blockMetadata.getMetadataList()) {
                Common.Metadata metadata = Common.Metadata.parseFrom(str);
                System.out.println(metadata);
            }*/
            //System.out.println(blockMetadata);

            // block data
            Common.BlockData blockData = blockInfo.getBlock().getData();
            for (ByteString data : blockData.getDataList()) {
                // 获取txID
                Common.Envelope envelope = Common.Envelope.parseFrom(data);
                Common.Payload payload = Common.Payload.parseFrom(envelope.getPayload());
                Common.ChannelHeader channelHeader = Common.ChannelHeader.parseFrom(payload.getHeader().getChannelHeader());
                System.out.println("txID: " + channelHeader.getTxId());
                //TransactionInfo transactionInfo = queryTransactionInfo(channelHeader.getTxId());

                if (channelHeader.getType() == 1) {
                    // 获取config
                    Configtx.ConfigEnvelope configEnvelope = Configtx.ConfigEnvelope.parseFrom(payload.getData());
                    configEnvelope.getConfig()
                            .getChannelGroup()
                            .getGroupsMap()
                            .forEach((s, configGroup) ->
                                    System.out.println(String.format("key: %s, value: %s.", s, configGroup.getModPolicy()))
                            );
                } else if (channelHeader.getType() == 3) {
                    // 获取transaction
                    FabricTransaction.Transaction transaction = FabricTransaction.Transaction.parseFrom(payload.getData());
                    for (FabricTransaction.TransactionAction transactionAction : transaction.getActionsList()) {
                        FabricTransaction.ChaincodeActionPayload chaincodeActionPayload = FabricTransaction.ChaincodeActionPayload.parseFrom(transactionAction.getPayload());
                        chaincodeActionPayload
                                .getAction()
                                .getEndorsementsList()
                                .forEach(endorsement -> {
                                    try {
                                        Identities.SerializedIdentity endorser = Identities.SerializedIdentity.parseFrom(endorsement.getEndorser());
                                        System.out.println(String.format("mspID: %s, idByte: %s.", endorser.getMspid(), endorser.getIdBytes().toStringUtf8()));
                                    } catch (InvalidProtocolBufferException e) {
                                        e.printStackTrace();
                                    }
                                    System.out.println();
                                });
                        System.out.println();
                    }
                } else {
                    throw new RuntimeException("Only able to decode ENDORSER_TRANSACTION and CONFIG type blocks");
                }
                System.out.println();
            }
            //System.out.println("height: " + i + ", blockData count: " + blockData.getDataCount());
        }
        System.out.println();

        /*System.out.println("==================================");
        TransactionInfo transactionInfo = queryTransactionInfo("d2433a1e17e542bf865cbe2d3dd952d6c3f4a66f46476d86622313d6fcefbd3d");
        System.out.println(transactionInfo.getEnvelope());*/
        return blockchainInfo;
    }

    public BlockInfo queryBlock(long blockNumber) throws ProposalException, InvalidArgumentException {
        BlockInfo blockInfo = chain.queryBlockByNumber(blockNumber);
        /*String previousHash = Hex.encodeHexString(blockInfo.getPreviousHash());
        System.out.println("queryBlockByNumber returned correct block with blockNumber " + blockInfo.getBlockNumber()
                + " \n previous_hash: " + previousHash);*/
        return blockInfo;
    }

    public BlockInfo queryBlock(byte[] hash) throws ProposalException, InvalidArgumentException {
        BlockInfo blockInfo = chain.queryBlockByHash(hash);
        System.out.println("queryBlockByHash returned block with blockNumber " + blockInfo.getBlockNumber());
        return null;
    }

    public BlockInfo queryBlock(String txID) throws ProposalException, InvalidArgumentException {
        BlockInfo blockInfo = chain.queryBlockByTransactionID(txID);
        System.out.println("queryBlockByTxID returned block with blockNumber " + blockInfo.getBlockNumber());
        return null;
    }

    public TransactionInfo queryTransactionInfo(String txID) throws InvalidArgumentException, ProposalException, InvalidProtocolBufferException {
        TransactionInfo transactionInfo = chain.queryTransactionByID(txID);
        Common.Payload payload = Common.Payload.parseFrom(transactionInfo.getProcessedTransaction().getTransactionEnvelope().getPayload());
        Common.ChannelHeader channelHeader = Common.ChannelHeader.parseFrom(payload.getHeader().getChannelHeader());
        System.out.println(channelHeader.getChannelId());
        System.out.println("time: " + channelHeader.getTimestamp().getNanos());
        return transactionInfo;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        try {
            String username = "admin";
            String password = "adminpw";
            Set<String> roles = null;
            String account = null;
            String affiliation = "peerOrg1";
            String mspID = "Org1MSP";
            //String mspID = "ziyun.MSP";
            String ordererName = "orderer0";

            hfClient = HFClient.createNewInstance();
            hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
            hfcaClient = new HFCAClient(CA_URL, null);
            hfcaClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

            Enrollment enrollment = hfcaClient.enroll(username, password);
            Customer customer = new Customer(username, enrollment, roles, account, affiliation, mspID);
            hfClient.setUserContext(customer);

            Orderer orderer = hfClient.newOrderer(ordererName, ORDERER_URL, null);
                // 只有第一次需要创建chain
            // IOException, InvalidArgumentException, TransactionException, ProposalException
            try {
                chain = createChain(configPath, orderer, chainName);
            } catch (IOException | InvalidArgumentException | TransactionException | ProposalException e) {
                log.warn("createChain error!", e);
                chain = getChain(chainName, orderer);
            } catch (Exception e) {
                log.error("createChain error!", e);
            }
            chainCodeID = ChainCodeID.newBuilder().setName(CHAIN_CODE_NAME)
                    .setVersion(CHAIN_CODE_VERSION)
                    .setPath(CHAIN_CODE_PATH).build();
        } catch (Exception e) {
            log.error("CargoService init error!", e);
            System.exit(1);
        }
    }

    @Override
    public void destroy() throws Exception {
        // chain shutdown
        chain.shutdown(true);
    }
}

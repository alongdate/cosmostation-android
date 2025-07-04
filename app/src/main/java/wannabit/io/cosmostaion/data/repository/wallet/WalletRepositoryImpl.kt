package wannabit.io.cosmostaion.data.repository.wallet

import com.babylon.btcstaking.v1.ParamsProto.Params
import com.babylon.epoching.v1.QueryProto.QueuedMessageResponse
import com.babylon.finality.v1.QueryProto.ActiveFinalityProvidersAtHeightResponse
import com.cosmos.auth.v1beta1.QueryProto
import com.cosmos.bank.v1beta1.QueryGrpc
import com.cosmos.bank.v1beta1.QueryProto.QueryAllBalancesRequest
import com.cosmos.base.query.v1beta1.PaginationProto
import com.cosmos.base.v1beta1.CoinProto
import com.cosmos.distribution.v1beta1.DistributionProto
import com.cosmos.distribution.v1beta1.QueryProto.QueryDelegationTotalRewardsRequest
import com.cosmos.staking.v1beta1.QueryGrpc.newBlockingStub
import com.cosmos.staking.v1beta1.QueryProto.QueryDelegatorDelegationsRequest
import com.cosmos.staking.v1beta1.QueryProto.QueryDelegatorUnbondingDelegationsRequest
import com.cosmos.staking.v1beta1.StakingProto
import com.cosmos.staking.v1beta1.StakingProto.DelegationResponse
import com.cosmwasm.wasm.v1.QueryProto.QuerySmartContractStateRequest
import com.cosmwasm.wasm.v1.QueryProto.QuerySmartContractStateResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.protobuf.ByteString
import com.ledger.live.ble.extension.toHexString
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.bouncycastle.util.encoders.Base64
import org.json.JSONObject
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import retrofit2.Response
import wannabit.io.cosmostaion.chain.BaseChain
import wannabit.io.cosmostaion.chain.CosmosEndPointType
import wannabit.io.cosmostaion.chain.cosmosClass.ChainBabylon
import wannabit.io.cosmostaion.chain.cosmosClass.ChainInitia
import wannabit.io.cosmostaion.chain.cosmosClass.ChainZenrock
import wannabit.io.cosmostaion.chain.cosmosClass.NEUTRON_VESTING_CONTRACT_ADDRESS
import wannabit.io.cosmostaion.chain.fetcher.BabylonFetcher
import wannabit.io.cosmostaion.chain.fetcher.IotaFetcher
import wannabit.io.cosmostaion.chain.fetcher.SuiFetcher
import wannabit.io.cosmostaion.chain.fetcher.accountInfos
import wannabit.io.cosmostaion.chain.fetcher.accountNumber
import wannabit.io.cosmostaion.chain.fetcher.balance
import wannabit.io.cosmostaion.chain.fetcher.btcReward
import wannabit.io.cosmostaion.chain.fetcher.chainHeight
import wannabit.io.cosmostaion.chain.fetcher.currentEpoch
import wannabit.io.cosmostaion.chain.fetcher.delegations
import wannabit.io.cosmostaion.chain.fetcher.epochMsg
import wannabit.io.cosmostaion.chain.fetcher.feeMarket
import wannabit.io.cosmostaion.chain.fetcher.finalityProviderWithVotingPower
import wannabit.io.cosmostaion.chain.fetcher.initiaDelegations
import wannabit.io.cosmostaion.chain.fetcher.initiaUnDelegations
import wannabit.io.cosmostaion.chain.fetcher.initiaValidators
import wannabit.io.cosmostaion.chain.fetcher.rewardAddress
import wannabit.io.cosmostaion.chain.fetcher.rewards
import wannabit.io.cosmostaion.chain.fetcher.sequence
import wannabit.io.cosmostaion.chain.fetcher.unDelegations
import wannabit.io.cosmostaion.chain.fetcher.validators
import wannabit.io.cosmostaion.chain.fetcher.zenrockDelegations
import wannabit.io.cosmostaion.chain.fetcher.zenrockUnDelegations
import wannabit.io.cosmostaion.chain.majorClass.ChainBitCoin86
import wannabit.io.cosmostaion.chain.majorClass.ChainIota
import wannabit.io.cosmostaion.chain.majorClass.ChainSui
import wannabit.io.cosmostaion.chain.testnetClass.ChainBabylonTestnet
import wannabit.io.cosmostaion.common.formatJsonString
import wannabit.io.cosmostaion.common.jsonRpcResponse
import wannabit.io.cosmostaion.common.safeApiCall
import wannabit.io.cosmostaion.data.api.RetrofitInstance.bitApi
import wannabit.io.cosmostaion.data.api.RetrofitInstance.bitExternalApi
import wannabit.io.cosmostaion.data.api.RetrofitInstance.lcdApi
import wannabit.io.cosmostaion.data.api.RetrofitInstance.mintscanApi
import wannabit.io.cosmostaion.data.api.RetrofitInstance.mintscanJsonApi
import wannabit.io.cosmostaion.data.model.req.Allocation
import wannabit.io.cosmostaion.data.model.req.AllocationReq
import wannabit.io.cosmostaion.data.model.req.JsonRpcRequest
import wannabit.io.cosmostaion.data.model.req.MoonPayReq
import wannabit.io.cosmostaion.data.model.req.NftInfo
import wannabit.io.cosmostaion.data.model.req.Rewards
import wannabit.io.cosmostaion.data.model.req.RewardsReq
import wannabit.io.cosmostaion.data.model.req.StarCw721TokenIdReq
import wannabit.io.cosmostaion.data.model.req.StarCw721TokenInfoReq
import wannabit.io.cosmostaion.data.model.req.VotingPower
import wannabit.io.cosmostaion.data.model.req.VotingPowerReq
import wannabit.io.cosmostaion.data.model.res.AppVersion
import wannabit.io.cosmostaion.data.model.res.AssetResponse
import wannabit.io.cosmostaion.data.model.res.Cw20Balance
import wannabit.io.cosmostaion.data.model.res.Cw20TokenResponse
import wannabit.io.cosmostaion.data.model.res.Cw721
import wannabit.io.cosmostaion.data.model.res.Cw721Response
import wannabit.io.cosmostaion.data.model.res.Erc20TokenResponse
import wannabit.io.cosmostaion.data.model.res.Grc20TokenResponse
import wannabit.io.cosmostaion.data.model.res.MoonPay
import wannabit.io.cosmostaion.data.model.res.NetworkResult
import wannabit.io.cosmostaion.data.model.res.NoticeResponse
import wannabit.io.cosmostaion.data.model.res.Price
import wannabit.io.cosmostaion.data.model.res.Token
import wannabit.io.cosmostaion.database.AppDatabase
import wannabit.io.cosmostaion.database.model.Password
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit


class WalletRepositoryImpl : WalletRepository {

    private var duration = 8L

    override suspend fun selectPassword(): NetworkResult<MutableList<Password>> {
        return safeApiCall(Dispatchers.IO) {
            AppDatabase.getInstance().passwordDao().selectAll()
        }
    }

    override suspend fun insertPassword(password: Password) {
        AppDatabase.getInstance().passwordDao().insert(password)
    }

    override suspend fun version(): NetworkResult<Response<AppVersion>> {
        return safeApiCall(Dispatchers.IO) {
            mintscanApi.version()
        }
    }

    override suspend fun price(currency: String): NetworkResult<List<Price>> {
        return safeApiCall(Dispatchers.IO) {
            mintscanApi.price(currency)
        }
    }

    override suspend fun usdPrice(): NetworkResult<List<Price>> {
        return safeApiCall(Dispatchers.IO) {
            mintscanApi.price("usd")
        }
    }

    override suspend fun asset(): NetworkResult<AssetResponse> {
        return safeApiCall(Dispatchers.IO) {
            mintscanApi.asset()
        }
    }

    override suspend fun param(): NetworkResult<JsonObject?> {
        return safeApiCall(Dispatchers.IO) {
            mintscanJsonApi.param()
        }
    }

    override suspend fun cw20(): NetworkResult<Cw20TokenResponse> {
        return safeApiCall(Dispatchers.IO) {
            mintscanApi.cw20token()
        }
    }

    override suspend fun erc20(): NetworkResult<Erc20TokenResponse> {
        return safeApiCall(Dispatchers.IO) {
            mintscanApi.erc20token()
        }
    }

    override suspend fun grc20(): NetworkResult<Grc20TokenResponse> {
        return safeApiCall(Dispatchers.IO) {
            mintscanApi.grc20token()
        }
    }

    override suspend fun cw721(): NetworkResult<Cw721Response> {
        return safeApiCall(Dispatchers.IO) {
            mintscanApi.cw721()
        }
    }

    override suspend fun auth(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<Unit> {
        return if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.cosmos.auth.v1beta1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request =
                QueryProto.QueryAccountRequest.newBuilder().setAddress(chain.address).build()
            safeApiCall(Dispatchers.IO) {
                chain.cosmosFetcher()?.cosmosAuth = stub.account(request).account
                chain.cosmosFetcher()?.cosmosAccountNumber =
                    stub.account(request).account.accountInfos().second
                chain.cosmosFetcher()?.cosmosSequence =
                    stub.account(request).account.accountInfos().third
            }

        } else {
            safeApiCall(Dispatchers.IO) {
                val response =
                    lcdApi(chain).lcdAuthInfo(chain.address).asJsonObject["account"].asJsonObject
                chain.cosmosFetcher()?.cosmosLcdAuth = response
                chain.cosmosFetcher()?.cosmosAccountNumber = response.accountNumber()
                chain.cosmosFetcher()?.cosmosSequence = response.sequence()
            }
        }
    }

    override suspend fun balance(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<MutableList<CoinProto.Coin>> {
        return if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val pageRequest = PaginationProto.PageRequest.newBuilder().setLimit(2000).build()
            val stub =
                QueryGrpc.newBlockingStub(channel).withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request = QueryAllBalancesRequest.newBuilder().setPagination(pageRequest)
                .setAddress(chain.address).build()
            safeApiCall(Dispatchers.IO) {
                stub.allBalances(request).balancesList
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdBalanceInfo(chain.address, "2000").balance()
            }
        }
    }

    override suspend fun spendableBalance(
        channel: ManagedChannel?,
        chain: BaseChain
    ): NetworkResult<MutableList<CoinProto.Coin>> {
        return if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val pageRequest = PaginationProto.PageRequest.newBuilder().setLimit(2000).build()
            val stub =
                QueryGrpc.newBlockingStub(channel).withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request =
                com.cosmos.bank.v1beta1.QueryProto.QuerySpendableBalancesRequest.newBuilder()
                    .setPagination(pageRequest).setAddress(chain.address).build()
            safeApiCall(Dispatchers.IO) {
                stub.spendableBalances(request).balancesList
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdSpendableBalanceInfo(chain.address, "2000").balance()
            }
        }
    }

    override suspend fun delegation(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<MutableList<DelegationResponse>> {
        return if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = newBlockingStub(channel).withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request =
                QueryDelegatorDelegationsRequest.newBuilder().setDelegatorAddr(chain.address)
                    .build()
            safeApiCall(Dispatchers.IO) {
                stub.delegatorDelegations(request).delegationResponsesList
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdDelegationInfo(chain.address).delegations()
            }
        }
    }

    override suspend fun unBonding(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<MutableList<StakingProto.UnbondingDelegation>> {
        return if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = newBlockingStub(channel).withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request = QueryDelegatorUnbondingDelegationsRequest.newBuilder()
                .setDelegatorAddr(chain.address).build()
            safeApiCall(Dispatchers.IO) {
                stub.delegatorUnbondingDelegations(request).unbondingResponsesList
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdUnBondingInfo(chain.address).unDelegations()
            }
        }
    }

    override suspend fun reward(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<MutableList<DistributionProto.DelegationDelegatorReward>> {
        return if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.cosmos.distribution.v1beta1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request =
                QueryDelegationTotalRewardsRequest.newBuilder().setDelegatorAddress(chain.address)
                    .build()
            safeApiCall(Dispatchers.IO) {
                stub.delegationTotalRewards(request).rewardsList
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdRewardInfo(chain.address).rewards()
            }
        }
    }

    override suspend fun rewardAddress(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<String> {
        return if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.cosmos.distribution.v1beta1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request =
                com.cosmos.distribution.v1beta1.QueryProto.QueryDelegatorWithdrawAddressRequest.newBuilder()
                    .setDelegatorAddress(chain.address).build()
            safeApiCall(Dispatchers.IO) {
                stub.delegatorWithdrawAddress(request).withdrawAddress
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdRewardAddressInfo(chain.address).rewardAddress()
            }
        }
    }

    override suspend fun baseFee(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<MutableList<CoinProto.DecCoin>>? {
        if (chain.isSupportFeeMarket() == false) {
            return null
        }
        return if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.feemarket.feemarket.v1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request =
                com.feemarket.feemarket.v1.QueryProto.GasPricesRequest.newBuilder().build()
            safeApiCall(Dispatchers.IO) {
                stub.gasPrices(request).pricesList
            }

        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdFeeMarketInfo().feeMarket()
            }
        }
    }

    override suspend fun bondedValidator(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<MutableList<StakingProto.Validator>> {
        return if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            channel?.let { managedChannel ->
                val pageRequest = PaginationProto.PageRequest.newBuilder().setLimit(500).build()
                val stub =
                    newBlockingStub(managedChannel).withDeadlineAfter(duration, TimeUnit.SECONDS)
                val request =
                    com.cosmos.staking.v1beta1.QueryProto.QueryValidatorsRequest.newBuilder()
                        .setPagination(pageRequest).setStatus("BOND_STATUS_BONDED").build()
                safeApiCall(Dispatchers.IO) {
                    stub.validators(request).validatorsList
                }

            } ?: run {
                safeApiCall(Dispatchers.IO) {
                    mutableListOf()
                }
            }

        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdBondedValidatorInfo()
                    .validators(StakingProto.BondStatus.BOND_STATUS_BONDED)
            }
        }
    }

    override suspend fun unBondedValidator(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<MutableList<StakingProto.Validator>> {
        return if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            channel?.let { managedChannel ->
                val pageRequest = PaginationProto.PageRequest.newBuilder().setLimit(500).build()
                val stub =
                    newBlockingStub(managedChannel).withDeadlineAfter(duration, TimeUnit.SECONDS)
                val request =
                    com.cosmos.staking.v1beta1.QueryProto.QueryValidatorsRequest.newBuilder()
                        .setPagination(pageRequest).setStatus("BOND_STATUS_UNBONDED").build()
                safeApiCall(Dispatchers.IO) {
                    stub.validators(request).validatorsList
                }

            } ?: run {
                safeApiCall(Dispatchers.IO) {
                    mutableListOf()
                }
            }

        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdUnBondedValidatorInfo()
                    .validators(StakingProto.BondStatus.BOND_STATUS_UNBONDED)
            }
        }
    }

    override suspend fun unBondingValidator(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<MutableList<StakingProto.Validator>> {
        return if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            channel?.let { managedChannel ->
                val pageRequest = PaginationProto.PageRequest.newBuilder().setLimit(500).build()
                val stub =
                    newBlockingStub(managedChannel).withDeadlineAfter(duration, TimeUnit.SECONDS)
                val request =
                    com.cosmos.staking.v1beta1.QueryProto.QueryValidatorsRequest.newBuilder()
                        .setPagination(pageRequest).setStatus("BOND_STATUS_UNBONDING").build()
                safeApiCall(Dispatchers.IO) {
                    stub.validators(request).validatorsList
                }

            } ?: run {
                safeApiCall(Dispatchers.IO) {
                    mutableListOf()
                }
            }

        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdUnBondingValidatorInfo()
                    .validators(StakingProto.BondStatus.BOND_STATUS_UNBONDING)
            }
        }
    }

    override suspend fun moonPay(data: MoonPayReq): NetworkResult<Response<MoonPay>> {
        return safeApiCall(Dispatchers.IO) {
            mintscanApi.moonPay(data)
        }
    }

    override suspend fun cw20Balance(
        channel: ManagedChannel?, chain: BaseChain, token: Token
    ) {
        val req = Cw20Balance(chain.address)
        val jsonData = Gson().toJson(req)
        val queryData = ByteString.copyFromUtf8(jsonData)

        if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.cosmwasm.wasm.v1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request = QuerySmartContractStateRequest.newBuilder().setAddress(token.address)
                .setQueryData(queryData).build()
            try {
                stub.smartContractState(request)?.let { response ->
                    val json = JSONObject(response.data.toStringUtf8())
                    token.amount = json.get("balance").toString()
                    token.fetched = true
                }
            } catch (e: Exception) {
                token.fetched = true
                token.amount = "0"
            }

        } else {
            val queryDataBase64 = Base64.toBase64String(queryData.toByteArray())
            try {
                lcdApi(chain).lcdContractInfo(token.address, queryDataBase64).let { response ->
                    token.amount = response["data"].asJsonObject["balance"].asString
                    token.fetched = true
                }

            } catch (e: Exception) {
                token.fetched = true
                token.amount = "0"
            }
        }
    }

    override suspend fun erc20Balance(chain: BaseChain, token: Token) {
        val params: MutableList<Type<*>> = ArrayList()
        params.add(Address(chain.evmAddress))

        try {
            val returnTypes = listOf<TypeReference<*>>(object : TypeReference<Uint256?>() {})
            val function = if (token.symbol == "BGT") {
                Function("unboostedBalanceOf", params, returnTypes)
            } else {
                Function("balanceOf", params, returnTypes)
            }

            val txData = FunctionEncoder.encode(function)
            val response = chain.web3j?.ethCall(
                Transaction.createEthCallTransaction(chain.evmAddress, token.address, txData),
                DefaultBlockParameterName.LATEST
            )?.sendAsync()?.get()
            val results = FunctionReturnDecoder.decode(response?.value, function.outputParameters)
            if (results.isNotEmpty()) {
                val balance = results[0].value as BigInteger
                token.amount = balance.toString()
            } else {
                token.amount = "0"
            }
            token.fetched = true

        } catch (e: Exception) {
            token.amount = "0"
            token.fetched = false
        }
    }

    override suspend fun grc20Balance(chain: BaseChain, grc20Token: Token) {
        val queryData = grc20Token.address + ".BalanceOf(\"${chain.address}\")"
        val queryDataBase64 = Base64.toBase64String(queryData.toByteArray())
        val grc20BalanceRequest = JsonRpcRequest(
            method = "abci_query", params = listOf("vm/qeval", queryDataBase64, "0", false)
        )
        val grc20BalanceResponse =
            jsonRpcResponse(chain.gnoRpcFetcher?.gnoRpc() ?: chain.mainUrl, grc20BalanceRequest)
        try {
            if (grc20BalanceResponse.isSuccessful) {
                val grc20BalanceJsonObject = Gson().fromJson(
                    grc20BalanceResponse.body?.string(), JsonObject::class.java
                )
                if (!grc20BalanceJsonObject.has("error")) {
                    val balanceResult =
                        grc20BalanceJsonObject["result"].asJsonObject["response"].asJsonObject["ResponseBase"].asJsonObject
                    val balanceData = balanceResult["Data"].asString
                    val decodeData = formatJsonString(String(Base64.decode(balanceData)))
                    val regex = "\\d+".toRegex()
                    val match = regex.find(decodeData)
                    grc20Token.amount = match?.value?.toLong().toString()
                    grc20Token.fetched = true

                } else {
                    grc20Token.amount = "0"
                    grc20Token.fetched = true
                }

            } else {
                grc20Token.amount = "0"
                grc20Token.fetched = true
            }

        } catch (e: Exception) {
            grc20Token.amount = "0"
            grc20Token.fetched = true
        }
    }

    override suspend fun vestingData(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<QuerySmartContractStateResponse> {
        val req = AllocationReq(Allocation(chain.address))
        val jsonData = Gson().toJson(req)
        val queryData = ByteString.copyFromUtf8(jsonData)

        return if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.cosmwasm.wasm.v1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request = QuerySmartContractStateRequest.newBuilder()
                .setAddress(NEUTRON_VESTING_CONTRACT_ADDRESS).setQueryData(queryData).build()
            safeApiCall(Dispatchers.IO) {
                stub.smartContractState(request)
            }

        } else {
            val queryDataBase64 = Base64.toBase64String(queryData.toByteArray())
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdContractInfo(NEUTRON_VESTING_CONTRACT_ADDRESS, queryDataBase64)
                    .let { response ->
                        val data = response["data"].asJsonObject
                        QuerySmartContractStateResponse.newBuilder()
                            .setData(ByteString.copyFromUtf8(Gson().toJson(data))).build()
                    }
            }
        }
    }

    override suspend fun vaultDeposit(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<String?> {
        val contractAddress = chain.getChainListParam()
            ?.get("vaults")?.asJsonArray?.get(0)?.asJsonObject?.get("address")?.asString
        val req = VotingPowerReq(VotingPower(chain.address))
        val jsonData = Gson().toJson(req)
        val queryData = ByteString.copyFromUtf8(jsonData)

        return if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.cosmwasm.wasm.v1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(8, TimeUnit.SECONDS)
            val request = QuerySmartContractStateRequest.newBuilder().setAddress(
                contractAddress
            ).setQueryData(queryData).build()
            safeApiCall(Dispatchers.IO) {
                stub.smartContractState(request)?.let { response ->
                    val json = JSONObject(response.data.toStringUtf8())
                    json.getString("power")
                }
            }
        } else {
            val queryDataBase64 = Base64.toBase64String(queryData.toByteArray())
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdContractInfo(contractAddress, queryDataBase64).let { response ->
                    response["data"].asJsonObject["power"].asString
                }
            }
        }
    }

    override suspend fun stakingRewards(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<BigDecimal?> {
        val contractAddress =
            chain.getChainListParam()?.getAsJsonObject("reward")?.get("address")?.asString ?: ""
        val req = RewardsReq(Rewards(chain.address))
        val jsonData = Gson().toJson(req)
        val queryData = ByteString.copyFromUtf8(jsonData)

        return if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.cosmwasm.wasm.v1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(8, TimeUnit.SECONDS)
            val request = QuerySmartContractStateRequest.newBuilder().setAddress(
                contractAddress
            ).setQueryData(queryData).build()
            safeApiCall(Dispatchers.IO) {
                stub.smartContractState(request)?.let { response ->
                    val json = JSONObject(response.data.toStringUtf8())
                    json.getJSONObject("pending_rewards").getString("amount").toBigDecimal()
                }
            }
        } else {
            val queryDataBase64 = Base64.toBase64String(queryData.toByteArray())
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdContractInfo(contractAddress, queryDataBase64).let { response ->
                    response["data"].asJsonObject["pending_rewards"].asJsonObject["amount"].asString.toBigDecimal()
                }
            }
        }
    }

    override suspend fun initiaDelegation(
        channel: ManagedChannel?, chain: ChainInitia
    ): NetworkResult<MutableList<com.initia.mstaking.v1.StakingProto.DelegationResponse>> {
        return if (chain.initiaFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.initia.mstaking.v1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request =
                com.initia.mstaking.v1.QueryProto.QueryDelegatorDelegationsRequest.newBuilder()
                    .setDelegatorAddr(chain.address).build()
            safeApiCall(Dispatchers.IO) {
                stub.delegatorDelegations(request).delegationResponsesList
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdInitiaDelegationInfo(chain.address).initiaDelegations()
            }
        }
    }

    override suspend fun initiaUnBonding(
        channel: ManagedChannel?, chain: ChainInitia
    ): NetworkResult<MutableList<com.initia.mstaking.v1.StakingProto.UnbondingDelegation>> {
        return if (chain.initiaFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.initia.mstaking.v1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request =
                com.initia.mstaking.v1.QueryProto.QueryDelegatorUnbondingDelegationsRequest.newBuilder()
                    .setDelegatorAddr(chain.address).build()
            safeApiCall(Dispatchers.IO) {
                stub.delegatorUnbondingDelegations(request).unbondingResponsesList
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdInitiaUnBondingInfo(chain.address).initiaUnDelegations()
            }
        }
    }

    override suspend fun initiaBondedValidator(
        channel: ManagedChannel?, chain: ChainInitia
    ): NetworkResult<MutableList<com.initia.mstaking.v1.StakingProto.Validator>> {
        return if (chain.initiaFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val pageRequest = PaginationProto.PageRequest.newBuilder().setLimit(500).build()
            val stub = com.initia.mstaking.v1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request = com.initia.mstaking.v1.QueryProto.QueryValidatorsRequest.newBuilder()
                .setPagination(pageRequest).setStatus("BOND_STATUS_BONDED").build()
            safeApiCall(Dispatchers.IO) {
                stub.validators(request).validatorsList
            }

        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdInitiaBondedValidatorInfo()
                    .initiaValidators(com.initia.mstaking.v1.StakingProto.BondStatus.BOND_STATUS_BONDED)
            }
        }
    }

    override suspend fun initiaUnBondedValidator(
        channel: ManagedChannel?, chain: ChainInitia
    ): NetworkResult<MutableList<com.initia.mstaking.v1.StakingProto.Validator>> {
        return if (chain.initiaFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val pageRequest = PaginationProto.PageRequest.newBuilder().setLimit(500).build()
            val stub = com.initia.mstaking.v1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request = com.initia.mstaking.v1.QueryProto.QueryValidatorsRequest.newBuilder()
                .setPagination(pageRequest).setStatus("BOND_STATUS_UNBONDED").build()
            safeApiCall(Dispatchers.IO) {
                stub.validators(request).validatorsList
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdInitiaUnBondedValidatorInfo()
                    .initiaValidators(com.initia.mstaking.v1.StakingProto.BondStatus.BOND_STATUS_UNBONDED)
            }
        }
    }

    override suspend fun initiaUnBondingValidator(
        channel: ManagedChannel?, chain: ChainInitia
    ): NetworkResult<MutableList<com.initia.mstaking.v1.StakingProto.Validator>> {
        return if (chain.initiaFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val pageRequest = PaginationProto.PageRequest.newBuilder().setLimit(500).build()
            val stub = com.initia.mstaking.v1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request = com.initia.mstaking.v1.QueryProto.QueryValidatorsRequest.newBuilder()
                .setPagination(pageRequest).setStatus("BOND_STATUS_UNBONDING").build()
            safeApiCall(Dispatchers.IO) {
                stub.validators(request).validatorsList
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdInitiaUnBondingValidatorInfo()
                    .initiaValidators(com.initia.mstaking.v1.StakingProto.BondStatus.BOND_STATUS_UNBONDING)
            }
        }
    }

    override suspend fun zenrockDelegation(
        channel: ManagedChannel?, chain: ChainZenrock
    ): NetworkResult<MutableList<com.zrchain.validation.StakingProto.DelegationResponse>> {
        return if (chain.zenrockFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.zrchain.validation.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request =
                com.zrchain.validation.QueryProto.QueryDelegatorDelegationsRequest.newBuilder()
                    .setDelegatorAddr(chain.address).build()
            safeApiCall(Dispatchers.IO) {
                stub.delegatorDelegations(request).delegationResponsesList
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdDelegationInfo(chain.address).zenrockDelegations()
            }
        }
    }

    override suspend fun zenrockUnBonding(
        channel: ManagedChannel?, chain: ChainZenrock
    ): NetworkResult<MutableList<com.zrchain.validation.StakingProto.UnbondingDelegation>> {
        return if (chain.zenrockFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.zrchain.validation.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request =
                com.zrchain.validation.QueryProto.QueryDelegatorUnbondingDelegationsRequest.newBuilder()
                    .setDelegatorAddr(chain.address).build()
            safeApiCall(Dispatchers.IO) {
                stub.delegatorUnbondingDelegations(request).unbondingResponsesList
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdUnBondingInfo(chain.address).zenrockUnDelegations()
            }
        }
    }

    override suspend fun zenrockBondedValidator(
        channel: ManagedChannel?, chain: ChainZenrock
    ): NetworkResult<MutableList<com.zrchain.validation.HybridValidationProto.ValidatorHV>> {
        return if (chain.zenrockFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val pageRequest = PaginationProto.PageRequest.newBuilder().setLimit(500).build()
            val stub = com.zrchain.validation.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request = com.zrchain.validation.QueryProto.QueryValidatorsRequest.newBuilder()
                .setPagination(pageRequest).setStatus("BOND_STATUS_BONDED").build()
            safeApiCall(Dispatchers.IO) {
                stub.validators(request).validatorsList
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdBondedValidatorInfo()
                    .validators(com.zrchain.validation.StakingProto.BondStatus.BOND_STATUS_BONDED)
            }
        }
    }

    override suspend fun zenrockUnBondedValidator(
        channel: ManagedChannel?, chain: ChainZenrock
    ): NetworkResult<MutableList<com.zrchain.validation.HybridValidationProto.ValidatorHV>> {
        return if (chain.zenrockFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            channel?.let {
                val pageRequest = PaginationProto.PageRequest.newBuilder().setLimit(500).build()
                val stub = com.zrchain.validation.QueryGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(duration, TimeUnit.SECONDS)
                val request = com.zrchain.validation.QueryProto.QueryValidatorsRequest.newBuilder()
                    .setPagination(pageRequest).setStatus("BOND_STATUS_UNBONDED").build()
                safeApiCall(Dispatchers.IO) {
                    stub.validators(request).validatorsList
                }

            } ?: run {
                safeApiCall(Dispatchers.IO) {
                    mutableListOf()
                }
            }

        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdUnBondedValidatorInfo()
                    .validators(com.zrchain.validation.StakingProto.BondStatus.BOND_STATUS_UNBONDED)
            }
        }
    }

    override suspend fun zenrockUnBondingValidator(
        channel: ManagedChannel?, chain: ChainZenrock
    ): NetworkResult<MutableList<com.zrchain.validation.HybridValidationProto.ValidatorHV>> {
        return if (chain.zenrockFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            channel?.let {
                val pageRequest = PaginationProto.PageRequest.newBuilder().setLimit(500).build()
                val stub = com.zrchain.validation.QueryGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(duration, TimeUnit.SECONDS)
                val request = com.zrchain.validation.QueryProto.QueryValidatorsRequest.newBuilder()
                    .setPagination(pageRequest).setStatus("BOND_STATUS_UNBONDING").build()
                safeApiCall(Dispatchers.IO) {
                    stub.validators(request).validatorsList
                }

            } ?: run {
                safeApiCall(Dispatchers.IO) {
                    mutableListOf()
                }
            }

        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdUnBondingValidatorInfo()
                    .validators(com.zrchain.validation.StakingProto.BondStatus.BOND_STATUS_UNBONDING)
            }
        }
    }

    override suspend fun oktAccountInfo(chain: BaseChain): NetworkResult<JsonObject?> {
        return safeApiCall(Dispatchers.IO) {
            lcdApi(chain).oktAccountInfo(chain.address)
        }
    }

    override suspend fun oktDeposit(chain: BaseChain): NetworkResult<JsonObject?> {
        return safeApiCall(Dispatchers.IO) {
            lcdApi(chain).oktDepositInfo(chain.address)
        }
    }

    override suspend fun oktWithdraw(chain: BaseChain): NetworkResult<JsonObject?> {
        return safeApiCall(Dispatchers.IO) {
            lcdApi(chain).oktWithdrawInfo(chain.address)
        }
    }

    override suspend fun evmBalance(chain: BaseChain): NetworkResult<String> {
        return safeApiCall(Dispatchers.IO) {
            val web3j = Web3j.build(HttpService(chain.evmRpcFetcher?.getEvmRpc()))
            val balance =
                web3j.ethGetBalance(chain.evmAddress, DefaultBlockParameterName.LATEST).send()
            balance.balance.toString()
        }
    }

    override suspend fun cw721TokenIds(
        channel: ManagedChannel?, chain: BaseChain, list: Cw721
    ): NetworkResult<JsonObject?> {
        val req = StarCw721TokenIdReq(wannabit.io.cosmostaion.data.model.req.Token(chain.address))
        val jsonData = Gson().toJson(req)
        val queryData = ByteString.copyFromUtf8(jsonData)

        if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.cosmwasm.wasm.v1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request =
                QuerySmartContractStateRequest.newBuilder().setAddress(list.contractAddress)
                    .setQueryData(queryData).build()
            return safeApiCall {
                stub.smartContractState(request)?.let { response ->
                    val tokenIds =
                        Gson().fromJson(response.data.toStringUtf8(), JsonObject::class.java)
                    if (tokenIds.asJsonObject["tokens"].asJsonArray.size() > 0) {
                        tokenIds
                    } else {
                        JsonObject()
                    }
                }
            }

        } else {
            val queryDataBase64 = Base64.toBase64String(queryData.toByteArray())
            lcdApi(chain).lcdContractInfo(
                list.contractAddress, queryDataBase64
            ).let { response ->
                return safeApiCall {
                    val tokens = response["data"].asJsonObject["tokens"].asJsonArray
                    if (tokens.size() > 0) {
                        response["data"].asJsonObject
                    } else {
                        JsonObject()
                    }
                }
            }
        }
    }

    override suspend fun cw721TokenInfo(
        channel: ManagedChannel?, chain: BaseChain, list: Cw721, tokenId: String
    ): NetworkResult<JsonObject?> {
        val req = StarCw721TokenInfoReq(NftInfo(tokenId))
        val jsonData = Gson().toJson(req)
        val queryData = ByteString.copyFromUtf8(jsonData)

        if (chain.cosmosFetcher?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.cosmwasm.wasm.v1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request =
                QuerySmartContractStateRequest.newBuilder().setAddress(list.contractAddress)
                    .setQueryData(queryData).build()
            return safeApiCall(Dispatchers.IO) {
                stub.smartContractState(request)?.let { response ->
                    Gson().fromJson(response.data.toStringUtf8(), JsonObject::class.java)
                }
            }

        } else {
            val queryDataBase64 = Base64.toBase64String(queryData.toByteArray())
            lcdApi(chain).lcdContractInfo(
                list.contractAddress, queryDataBase64
            ).let { response ->
                return safeApiCall {
                    response.asJsonObject["data"].asJsonObject
                }
            }
        }
    }

    override suspend fun cw721TokenDetail(
        chain: BaseChain, contractAddress: String, tokenId: String
    ): NetworkResult<JsonObject> {
        return safeApiCall(Dispatchers.IO) {
            mintscanJsonApi.cw721Detail(chain.apiName, contractAddress, tokenId)
        }
    }

    override suspend fun ecoSystemInfo(): NetworkResult<MutableList<JsonObject>> {
        return safeApiCall(Dispatchers.IO) {
            mintscanJsonApi.ecoSystemInfo()
        }
    }

    override suspend fun notice(): NetworkResult<NoticeResponse> {
        return safeApiCall(Dispatchers.IO) {
            mintscanApi.notice()
        }
    }

    override suspend fun suiBalance(
        fetcher: SuiFetcher, chain: ChainSui
    ): NetworkResult<JsonObject?> {
        return try {
            val suiAllBalanceRequest = JsonRpcRequest(
                method = "suix_getAllBalances", params = listOf(chain.mainAddress)
            )
            val suiAllBalanceResponse = jsonRpcResponse(fetcher.suiRpc(), suiAllBalanceRequest)
            val suiAllBalanceJsonObject = Gson().fromJson(
                suiAllBalanceResponse.body?.string(), JsonObject::class.java
            )
            safeApiCall(Dispatchers.IO) {
                suiAllBalanceJsonObject
            }

        } catch (e: Exception) {
            safeApiCall(Dispatchers.IO) {
                null
            }
        }
    }

    override suspend fun suiSystemState(
        fetcher: SuiFetcher, chain: ChainSui
    ): NetworkResult<JsonObject> {
        return try {
            val suiLatestSuiSystemRequest = JsonRpcRequest(
                method = "suix_getLatestSuiSystemState", params = listOf()
            )
            val suiLatestSuiSystemResponse =
                jsonRpcResponse(fetcher.suiRpc(), suiLatestSuiSystemRequest)
            val suiLatestSuiSystemJsonObject = Gson().fromJson(
                suiLatestSuiSystemResponse.body?.string(), JsonObject::class.java
            )
            safeApiCall(Dispatchers.IO) {
                suiLatestSuiSystemJsonObject
            }

        } catch (e: Exception) {
            safeApiCall(Dispatchers.IO) {
                JsonObject()
            }
        }
    }

    override suspend fun suiOwnedObject(
        fetcher: SuiFetcher, chain: ChainSui, cursor: String?
    ) {
        val params = if (cursor == null) {
            listOf(
                chain.mainAddress, mapOf(
                    "filter" to null, "options" to mapOf(
                        "showContent" to true, "showDisplay" to true, "showType" to true
                    )
                )
            )
        } else {
            listOf(
                chain.mainAddress, mapOf(
                    "filter" to null, "options" to mapOf(
                        "showContent" to true, "showDisplay" to true, "showType" to true
                    )
                ), cursor
            )
        }

        try {
            val suiOwnedObjectRequest = JsonRpcRequest(
                method = "suix_getOwnedObjects", params = params
            )
            val suiOwnedObjectResponse = jsonRpcResponse(fetcher.suiRpc(), suiOwnedObjectRequest)
            val suiOwnedObjectJsonObject = Gson().fromJson(
                suiOwnedObjectResponse.body?.string(), JsonObject::class.java
            )
            suiOwnedObjectJsonObject["result"].asJsonObject["data"].asJsonArray.forEach { data ->
                fetcher.suiObjects.add(data.asJsonObject)
            }
            if (suiOwnedObjectJsonObject["result"].asJsonObject["hasNextPage"].asBoolean && suiOwnedObjectJsonObject["result"].asJsonObject["nextCursor"].asString != null) {
                suiOwnedObject(
                    fetcher,
                    chain,
                    suiOwnedObjectJsonObject["result"].asJsonObject["nextCursor"].asString
                )
            }

        } catch (e: Exception) {

        }
    }

    override suspend fun suiStakes(
        fetcher: SuiFetcher, chain: ChainSui
    ): NetworkResult<JsonObject> {
        return try {
            val suiStakesRequest = JsonRpcRequest(
                method = "suix_getStakes", params = listOf(chain.mainAddress)
            )
            val suiStakesResponse = jsonRpcResponse(fetcher.suiRpc(), suiStakesRequest)
            val suiStakesJsonObject = Gson().fromJson(
                suiStakesResponse.body?.string(), JsonObject::class.java
            )
            return safeApiCall(Dispatchers.IO) {
                suiStakesJsonObject
            }

        } catch (e: Exception) {
            safeApiCall(Dispatchers.IO) {
                JsonObject()
            }
        }
    }

    override suspend fun suiCoinMetadata(
        fetcher: SuiFetcher, chain: ChainSui, coinType: String?
    ): NetworkResult<JsonObject> {
        return try {
            val suiCoinMetadataRequest = JsonRpcRequest(
                method = "suix_getCoinMetadata", params = listOf(coinType)
            )
            val suiCoinMetadataResponse = jsonRpcResponse(fetcher.suiRpc(), suiCoinMetadataRequest)
            val suiCoinMetadataJsonObject = Gson().fromJson(
                suiCoinMetadataResponse.body?.string(), JsonObject::class.java
            )
            return safeApiCall(Dispatchers.IO) {
                suiCoinMetadataJsonObject
            }
        } catch (e: Exception) {
            safeApiCall(Dispatchers.IO) {
                JsonObject()
            }
        }
    }

    override suspend fun suiApys(
        fetcher: SuiFetcher, chain: ChainSui
    ): NetworkResult<MutableList<JsonObject>> {
        return try {
            val suiApysRequest = JsonRpcRequest(
                method = "suix_getValidatorsApy", params = listOf()
            )
            val suiApysResponse = jsonRpcResponse(fetcher.suiRpc(), suiApysRequest)
            val suiApysJsonObject = Gson().fromJson(
                suiApysResponse.body?.string(), JsonObject::class.java
            )
            val result = mutableListOf<JsonObject>()
            suiApysJsonObject["result"].asJsonObject["apys"].asJsonArray.forEach { apy ->
                result.add(apy.asJsonObject)
            }
            safeApiCall(Dispatchers.IO) {
                result
            }
        } catch (e: Exception) {
            safeApiCall(Dispatchers.IO) {
                mutableListOf()
            }
        }
    }

    override suspend fun iotaBalance(
        fetcher: IotaFetcher, chain: ChainIota
    ): NetworkResult<JsonObject?> {
        return try {
            val iotaAllBalanceRequest = JsonRpcRequest(
                method = "iotax_getAllBalances", params = listOf(chain.mainAddress)
            )
            val iotaAllBalanceResponse = jsonRpcResponse(fetcher.iotaRpc(), iotaAllBalanceRequest)
            val iotaAllBalanceJsonObject = Gson().fromJson(
                iotaAllBalanceResponse.body?.string(), JsonObject::class.java
            )
            safeApiCall(Dispatchers.IO) {
                iotaAllBalanceJsonObject
            }

        } catch (e: Exception) {
            safeApiCall(Dispatchers.IO) {
                null
            }
        }
    }

    override suspend fun iotaSystemState(
        fetcher: IotaFetcher, chain: ChainIota
    ): NetworkResult<JsonObject> {
        return try {
            val iotaLatestIotaSystemRequest = JsonRpcRequest(
                method = "iotax_getLatestIotaSystemState", params = listOf()
            )
            val iotaLatestIotaSystemResponse =
                jsonRpcResponse(fetcher.iotaRpc(), iotaLatestIotaSystemRequest)
            val iotaLatestIotaSystemJsonObject = Gson().fromJson(
                iotaLatestIotaSystemResponse.body?.string(), JsonObject::class.java
            )
            safeApiCall(Dispatchers.IO) {
                iotaLatestIotaSystemJsonObject
            }

        } catch (e: Exception) {
            safeApiCall(Dispatchers.IO) {
                JsonObject()
            }
        }
    }

    override suspend fun iotaOwnedObject(fetcher: IotaFetcher, chain: ChainIota, cursor: String?) {
        val params = if (cursor == null) {
            listOf(
                chain.mainAddress, mapOf(
                    "filter" to null, "options" to mapOf(
                        "showContent" to true, "showDisplay" to true, "showType" to true
                    )
                )
            )
        } else {
            listOf(
                chain.mainAddress, mapOf(
                    "filter" to null, "options" to mapOf(
                        "showContent" to true, "showDisplay" to true, "showType" to true
                    )
                ), cursor
            )
        }

        try {
            val iotaOwnedObjectRequest = JsonRpcRequest(
                method = "iotax_getOwnedObjects", params = params
            )
            val iotaOwnedObjectResponse = jsonRpcResponse(fetcher.iotaRpc(), iotaOwnedObjectRequest)
            val iotaOwnedObjectJsonObject = Gson().fromJson(
                iotaOwnedObjectResponse.body?.string(), JsonObject::class.java
            )
            iotaOwnedObjectJsonObject["result"].asJsonObject["data"].asJsonArray.forEach { data ->
                fetcher.iotaObjects.add(data.asJsonObject)
            }
            if (iotaOwnedObjectJsonObject["result"].asJsonObject["hasNextPage"].asBoolean && iotaOwnedObjectJsonObject["result"].asJsonObject["nextCursor"].asString != null) {
                iotaOwnedObject(
                    fetcher,
                    chain,
                    iotaOwnedObjectJsonObject["result"].asJsonObject["nextCursor"].asString
                )
            }

        } catch (e: Exception) {

        }
    }

    override suspend fun iotaStakes(
        fetcher: IotaFetcher, chain: ChainIota
    ): NetworkResult<JsonObject> {
        return try {
            val iotaStakesRequest = JsonRpcRequest(
                method = "iotax_getStakes", params = listOf(chain.mainAddress)
            )
            val iotaStakesResponse = jsonRpcResponse(fetcher.iotaRpc(), iotaStakesRequest)
            val iotaStakesJsonObject = Gson().fromJson(
                iotaStakesResponse.body?.string(), JsonObject::class.java
            )
            return safeApiCall(Dispatchers.IO) {
                iotaStakesJsonObject
            }

        } catch (e: Exception) {
            safeApiCall(Dispatchers.IO) {
                JsonObject()
            }
        }
    }

    override suspend fun iotaCoinMetadata(
        fetcher: IotaFetcher, chain: ChainIota, coinType: String?
    ): NetworkResult<JsonObject> {
        return try {
            val iotaCoinMetadataRequest = JsonRpcRequest(
                method = "iotax_getCoinMetadata", params = listOf(coinType)
            )
            val iotaCoinMetadataResponse =
                jsonRpcResponse(fetcher.iotaRpc(), iotaCoinMetadataRequest)
            val iotaCoinMetadataJsonObject = Gson().fromJson(
                iotaCoinMetadataResponse.body?.string(), JsonObject::class.java
            )
            return safeApiCall(Dispatchers.IO) {
                iotaCoinMetadataJsonObject
            }
        } catch (e: Exception) {
            safeApiCall(Dispatchers.IO) {
                JsonObject()
            }
        }
    }

    override suspend fun iotaApys(
        fetcher: IotaFetcher, chain: ChainIota
    ): NetworkResult<MutableList<JsonObject>> {
        return try {
            val iotaApysRequest = JsonRpcRequest(
                method = "iotax_getValidatorsApy", params = listOf()
            )
            val iotaApysResponse = jsonRpcResponse(fetcher.iotaRpc(), iotaApysRequest)
            val iotaApysJsonObject = Gson().fromJson(
                iotaApysResponse.body?.string(), JsonObject::class.java
            )
            val result = mutableListOf<JsonObject>()
            iotaApysJsonObject["result"].asJsonObject["apys"].asJsonArray.forEach { apy ->
                result.add(apy.asJsonObject)
            }
            safeApiCall(Dispatchers.IO) {
                result
            }
        } catch (e: Exception) {
            safeApiCall(Dispatchers.IO) {
                mutableListOf()
            }
        }
    }

    override suspend fun bitBalance(chain: ChainBitCoin86): NetworkResult<JsonObject> {
        return safeApiCall(Dispatchers.IO) {
            bitApi(chain).bitBalance(chain.mainAddress)
        }
    }

    override suspend fun bitStakingBalance(chain: BaseChain): NetworkResult<JsonObject> {
        return safeApiCall(Dispatchers.IO) {
            bitExternalApi(chain).bitStakingBalance(
                chain.publicKey?.toHexString()?.substring(2).toString()
            )
        }
    }

    override suspend fun rpcAuth(chain: BaseChain): NetworkResult<okhttp3.Response> {
        val authRequest = JsonRpcRequest(
            method = "abci_query", params = listOf("auth/accounts/${chain.address}", "", "0", false)
        )
        return safeApiCall(Dispatchers.IO) {
            jsonRpcResponse(chain.gnoRpcFetcher?.gnoRpc() ?: chain.mainUrl, authRequest)
        }
    }

    override suspend fun btcStakingStatus(chain: BaseChain): NetworkResult<MutableList<JsonObject>?> {
        try {
            val result: MutableList<JsonObject> = mutableListOf()
            var searchAfter: String? = ""

            do {
                val response = mintscanJsonApi.bitStakedStatus(
                    chain.apiName, chain.address, "60", searchAfter
                )?.asJsonObject?.get("data")?.asJsonArray

                response?.forEach { element ->
                    if (element.isJsonObject) {
                        result.add(element.asJsonObject)
                    }
                }

                searchAfter = if (response?.size() == 60) {
                    result[result.size - 1].asJsonObject["search_after"].asString
                } else {
                    ""
                }

            } while (searchAfter != "")

            return safeApiCall(Dispatchers.IO) {
                result
            }

        } catch (e: Exception) {
            return safeApiCall(Dispatchers.IO) {
                mutableListOf()
            }
        }
    }

    override suspend fun btcReward(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<MutableList<CoinProto.Coin>> {
        return try {
            if (chain.cosmosFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
                val stub = com.babylon.incentive.QueryGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(duration, TimeUnit.SECONDS)
                val request = com.babylon.incentive.QueryProto.QueryRewardGaugesRequest.newBuilder()
                    .setAddress(chain.address).build()
                val response = stub.rewardGauges(request).rewardGaugesMap

                val btcRewards: MutableList<CoinProto.Coin> = mutableListOf()
                for (key in response.keys) {
                    val btcDelegation = response[key]
                    if (btcDelegation?.coinsList?.isNotEmpty() == true && btcDelegation.withdrawnCoinsList.isNotEmpty()) {
                        btcDelegation.coinsList.forEach { coin ->
                            btcDelegation.withdrawnCoinsList.forEach { withdraw ->
                                if (coin.denom == withdraw.denom) {
                                    val reward = coin.amount.toBigDecimal()
                                        .subtract(withdraw.amount.toBigDecimal())
                                    btcRewards.add(
                                        CoinProto.Coin.newBuilder().setDenom(coin.denom)
                                            .setAmount(reward.toString()).build()
                                    )
                                }
                            }
                        }

                    } else if (btcDelegation?.coinsList?.isNotEmpty() == true) {
                        btcDelegation.coinsList.forEach { coin ->
                            btcRewards.add(
                                CoinProto.Coin.newBuilder().setDenom(coin.denom)
                                    .setAmount(coin.amount).build()
                            )
                        }

                    } else {
                        btcRewards.add(
                            CoinProto.Coin.newBuilder().setDenom(chain.stakeDenom).setAmount("0")
                                .build()
                        )
                    }
                }

                safeApiCall(Dispatchers.IO) {
                    btcRewards
                }

            } else {
                safeApiCall(Dispatchers.IO) {
                    lcdApi(chain).lcdBtcReward(chain.address).btcReward(chain.stakeDenom)
                }
            }

        } catch (e: Exception) {
            safeApiCall(Dispatchers.IO) {
                mutableListOf()
            }
        }
    }

    override suspend fun chainHeight(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<Long> {
        return if (chain.cosmosFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.cosmos.base.node.v1beta1.ServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request = com.cosmos.base.node.v1beta1.QueryProto.StatusRequest.newBuilder().build()
            safeApiCall(Dispatchers.IO) {
                stub.status(request).height
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdChainHeight().chainHeight()
            }
        }
    }

    override suspend fun currentEpoch(
        channel: ManagedChannel?, chain: BaseChain
    ): NetworkResult<com.babylon.epoching.v1.QueryProto.QueryCurrentEpochResponse> {
        return if (chain.cosmosFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.babylon.epoching.v1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request =
                com.babylon.epoching.v1.QueryProto.QueryCurrentEpochRequest.newBuilder().build()
            safeApiCall(Dispatchers.IO) {
                stub.currentEpoch(request)
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdCurrentEpoch().currentEpoch()
            }
        }
    }

    override suspend fun epochMessage(
        channel: ManagedChannel?,
        chain: BaseChain,
        epoch: Long,
    ): NetworkResult<MutableList<QueuedMessageResponse>> {
        return if (chain.cosmosFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val pageRequest = PaginationProto.PageRequest.newBuilder().setLimit(500).build()
            val stub = com.babylon.epoching.v1.QueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)
            val request = com.babylon.epoching.v1.QueryProto.QueryEpochMsgsRequest.newBuilder()
                .setEpochNum(epoch).setPagination(pageRequest).build()
            safeApiCall(Dispatchers.IO) {
                stub.epochMsgs(request).msgsList
            }
        } else {
            safeApiCall(Dispatchers.IO) {
                lcdApi(chain).lcdEpochMsg(epoch).epochMsg(chain)
            }
        }
    }

    override suspend fun epochMessageType(
        channel: ManagedChannel?, chain: BaseChain, epochMsgs: MutableList<QueuedMessageResponse>?
    ): NetworkResult<MutableList<BabylonFetcher.BabylonEpochTxType>?> {
        return if (chain.cosmosFetcher()?.endPointType(chain) == CosmosEndPointType.USE_GRPC) {
            val stub = com.cosmos.tx.v1beta1.ServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(duration, TimeUnit.SECONDS)

            coroutineScope {
                val deferredResults = epochMsgs?.map { epochMsg ->
                    async {
                        val request = com.cosmos.tx.v1beta1.ServiceProto.GetTxRequest.newBuilder()
                            .setHash(epochMsg.txId).build()
                        val tx = stub.getTx(request).tx

                        tx.body.messagesList.mapNotNull { msg ->
                            val type = msg.typeUrl
                            val validator: String?
                            val amount: CoinProto.Coin?
                            var creationHeight: Long = 0

                            when {
                                type.contains("MsgWrappedDelegate") -> {
                                    val msgValue =
                                        com.babylon.epoching.v1.TxProto.MsgWrappedDelegate.parseFrom(
                                            msg.value
                                        )
                                    validator = msgValue.msg.validatorAddress
                                    amount = msgValue.msg.amount
                                }

                                type.contains("MsgWrappedUndelegate") -> {
                                    val msgValue =
                                        com.babylon.epoching.v1.TxProto.MsgWrappedUndelegate.parseFrom(
                                            msg.value
                                        )
                                    validator = msgValue.msg.validatorAddress
                                    amount = msgValue.msg.amount
                                }

                                type.contains("MsgWrappedBeginRedelegate") -> {
                                    val msgValue =
                                        com.babylon.epoching.v1.TxProto.MsgWrappedBeginRedelegate.parseFrom(
                                            msg.value
                                        )
                                    validator = msgValue.msg.validatorDstAddress
                                    amount = msgValue.msg.amount
                                }

                                type.contains("MsgWrappedCancelUnbondingDelegation") -> {
                                    val msgValue =
                                        com.babylon.epoching.v1.TxProto.MsgWrappedCancelUnbondingDelegation.parseFrom(
                                            msg.value
                                        )
                                    validator = msgValue.msg.validatorAddress
                                    amount = msgValue.msg.amount
                                    creationHeight = msgValue.msg.creationHeight
                                }

                                else -> return@mapNotNull null
                            }

                            BabylonFetcher.BabylonEpochTxType(
                                type, validator, amount, creationHeight
                            )
                        }
                    }
                }
                safeApiCall(Dispatchers.IO) {
                    val results = deferredResults?.awaitAll()?.flatten()
                    results?.toMutableList()
                }
            }

        } else {
            coroutineScope {
                val deferredResults = epochMsgs?.map { epochMsg ->
                    async {
                        val txInfo = lcdApi(chain).lcdEpochTxType(epochMsg.txId)

                        txInfo["tx"].asJsonObject["body"].asJsonObject["messages"].asJsonArray.mapNotNull { msg ->
                            val type = msg.asJsonObject["@type"].asString
                            val validator: String?
                            val amount: CoinProto.Coin?
                            var creationHeight: Long = 0

                            when {
                                type.contains("MsgWrappedDelegate") -> {
                                    validator =
                                        msg.asJsonObject["msg"].asJsonObject["validator_address"].asString
                                    amount = CoinProto.Coin.newBuilder()
                                        .setDenom(msg.asJsonObject["msg"].asJsonObject["amount"].asJsonObject["denom"].asString)
                                        .setAmount(msg.asJsonObject["msg"].asJsonObject["amount"].asJsonObject["amount"].asString)
                                        .build()
                                }

                                type.contains("MsgWrappedUndelegate") -> {
                                    validator =
                                        msg.asJsonObject["msg"].asJsonObject["validator_address"].asString
                                    amount = CoinProto.Coin.newBuilder()
                                        .setDenom(msg.asJsonObject["msg"].asJsonObject["amount"].asJsonObject["denom"].asString)
                                        .setAmount(msg.asJsonObject["msg"].asJsonObject["amount"].asJsonObject["amount"].asString)
                                        .build()
                                }

                                type.contains("MsgWrappedBeginRedelegate") -> {
                                    validator =
                                        msg.asJsonObject["msg"].asJsonObject["validator_dst_address"].asString
                                    amount = CoinProto.Coin.newBuilder()
                                        .setDenom(msg.asJsonObject["msg"].asJsonObject["amount"].asJsonObject["denom"].asString)
                                        .setAmount(msg.asJsonObject["msg"].asJsonObject["amount"].asJsonObject["amount"].asString)
                                        .build()
                                }

                                type.contains("MsgWrappedCancelUnbondingDelegation") -> {
                                    validator =
                                        msg.asJsonObject["msg"].asJsonObject["validator_address"].asString
                                    amount = CoinProto.Coin.newBuilder()
                                        .setDenom(msg.asJsonObject["msg"].asJsonObject["amount"].asJsonObject["denom"].asString)
                                        .setAmount(msg.asJsonObject["msg"].asJsonObject["amount"].asJsonObject["amount"].asString)
                                        .build()
                                    creationHeight =
                                        msg.asJsonObject["msg"].asJsonObject["creation_height"].asString.toLong()
                                }

                                else -> return@mapNotNull null
                            }

                            BabylonFetcher.BabylonEpochTxType(
                                type, validator, amount, creationHeight
                            )
                        }
                    }
                }

                safeApiCall(Dispatchers.IO) {
                    val results = deferredResults?.awaitAll()?.flatten()
                    results?.toMutableList()
                }
            }
        }
    }

    override suspend fun statusHeight(
        channel: ManagedChannel?,
    ): NetworkResult<Long> {
        val stub = com.cosmos.base.node.v1beta1.ServiceGrpc.newBlockingStub(channel)
            .withDeadlineAfter(duration, TimeUnit.SECONDS)
        val request = com.cosmos.base.node.v1beta1.QueryProto.StatusRequest.newBuilder().build()
        return safeApiCall(Dispatchers.IO) {
            stub.status(request).height
        }
    }

    override suspend fun btcFinalityVotingPower(
        chain: BaseChain, height: Long
    ): NetworkResult<MutableList<ActiveFinalityProvidersAtHeightResponse>> {
        return safeApiCall(Dispatchers.IO) {
            val babylonChain = if (chain.isTestnet) {
                ChainBabylonTestnet()
            } else {
                ChainBabylon()
            }
            lcdApi(babylonChain).btcFinalityVotingPower(height).finalityProviderWithVotingPower()
        }
    }

    override suspend fun btcFinalityProviders(
        channel: ManagedChannel?
    ): NetworkResult<MutableList<com.babylon.btcstaking.v1.QueryProto.FinalityProviderResponse>> {
        val pageRequest = PaginationProto.PageRequest.newBuilder().setLimit(500).build()
        val stub = com.babylon.btcstaking.v1.QueryGrpc.newBlockingStub(channel)
            .withDeadlineAfter(duration, TimeUnit.SECONDS)
        val request =
            com.babylon.btcstaking.v1.QueryProto.QueryFinalityProvidersRequest.newBuilder()
                .setPagination(pageRequest).build()
        return safeApiCall(Dispatchers.IO) {
            stub.finalityProviders(request).finalityProvidersList
        }
    }

    override suspend fun btcParams(channel: ManagedChannel?): NetworkResult<Params> {
        val stub = com.babylon.btcstaking.v1.QueryGrpc.newBlockingStub(channel)
            .withDeadlineAfter(duration, TimeUnit.SECONDS)
        val request = com.babylon.btcstaking.v1.QueryProto.QueryParamsRequest.newBuilder().build()
        return safeApiCall(Dispatchers.IO) {
            stub.params(request).params
        }
    }

    override suspend fun btcNetworkInfo(chain: BaseChain): NetworkResult<JsonObject> {
        return safeApiCall(Dispatchers.IO) {
            bitExternalApi(chain).bitNetworkInfo()
        }
    }

    override suspend fun btcClientTip(chain: BaseChain): NetworkResult<JsonObject> {
        return safeApiCall(Dispatchers.IO) {
            val babylonChain = if (chain.isTestnet) {
                ChainBabylonTestnet()
            } else {
                ChainBabylon()
            }
            lcdApi(babylonChain).btcClientTioHeight()
        }
    }

    override suspend fun btcFee(chain: ChainBitCoin86): NetworkResult<JsonObject> {
        return safeApiCall(Dispatchers.IO) {
            bitApi(chain).btcFee()
        }
    }

    override suspend fun mempoolUtxo(chain: ChainBitCoin86): NetworkResult<MutableList<JsonObject>> {
        return safeApiCall(Dispatchers.IO) {
            bitApi(chain).bitUtxo(chain.mainAddress)
        }
    }

    override suspend fun mempoolIsValidAddress(chain: ChainBitCoin86): NetworkResult<JsonObject> {
        return safeApiCall(Dispatchers.IO) {
            bitApi(chain).bitIsValidAddress(chain.mainAddress)
        }
    }
}
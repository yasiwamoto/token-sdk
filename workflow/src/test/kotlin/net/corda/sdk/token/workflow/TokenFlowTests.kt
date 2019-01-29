package net.corda.sdk.token.workflow

import net.corda.core.contracts.StateAndRef
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.sdk.token.contracts.types.TokenPointer
import net.corda.sdk.token.contracts.utilities.of
import net.corda.sdk.token.money.GBP
import net.corda.sdk.token.workflow.statesAndContracts.House
import net.corda.sdk.token.workflow.utilities.getDistributionList
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class TokenFlowTests : MockNetworkTest(numberOfNodes = 3) {

    lateinit var A: StartedMockNode
    lateinit var B: StartedMockNode
    lateinit var I: StartedMockNode

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        I = nodes[2]
    }

    @Test
    fun `create evolvable token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()))
        val tx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val token = tx.singleOutput<House>()
        assertEquals(house, token.state.data)
    }

    @Test
    fun `create and update evolvable token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()))
        val tx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val oldToken = tx.singleOutput<House>()
        // Propose an update to the token.
        val proposedToken = house.copy(valuation = 950_000.GBP)
        val updateTx = I.updateEvolvableToken(oldToken, proposedToken).getOrThrow()
        val newToken = updateTx.singleOutput<House>()
        assertEquals(proposedToken, newToken.state.data)
    }

    @Test
    fun `create evolvable token, then issue evolvable token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()))
        val createTokenTx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val token = createTokenTx.singleOutput<House>()
        // Issue amount of the token.
        val housePointer: TokenPointer<House> = house.toPointer()
        val issueTokenAmountTx = I.issueToken(housePointer, A, NOTARY, 100 of housePointer).getOrThrow()
        A.watchForTransaction(issueTokenAmountTx.id).getOrThrow()
        // Check to see that A was added to I's distribution list.
        val distributionList = I.transaction { getDistributionList(I.services, token.linearId()) }
        val distributionRecord = distributionList.single()
        assertEquals(A.legalIdentity(), distributionRecord.party)
        assertEquals(token.linearId().id, distributionRecord.linearId)
        val houseQuery = A.services.vaultService.queryBy<House>().states
        houseQuery.forEach(::println)
    }

    @Test
    fun `check token recipient also receives token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()))
        val createTokenTx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val houseToken: StateAndRef<House> = createTokenTx.singleOutput()
        // Issue amount of the token.
        val housePointer: TokenPointer<House> = house.toPointer()
        val issueTokenAmountTx = I.issueToken(housePointer, A, NOTARY, 100 of housePointer).getOrThrow()
        // Wait for the transaction to be recorded by A.
        A.watchForTransaction(issueTokenAmountTx.id).getOrThrow()
        val houseQuery = A.services.vaultService.queryBy<House>().states
        assertEquals(houseToken, houseQuery.single())
    }

    @Test
    fun `create evolvable token, then issue tokens, then update token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()))
        val createTokenTx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val token = createTokenTx.singleOutput<House>()
        // Issue amount of the token.
        val housePointer: TokenPointer<House> = house.toPointer()
        val issueTokenAmountTx = I.issueToken(housePointer, A, NOTARY, 100 of housePointer).getOrThrow()
        A.watchForTransaction(issueTokenAmountTx.id).toCompletableFuture().getOrThrow()
        // Update the token.
        val proposedToken = house.copy(valuation = 950_000.GBP)
        val updateTx = I.updateEvolvableToken(token, proposedToken).getOrThrow()
        // Wait to see if A is sent the updated token.
        val updatedToken = A.watchForTransaction(updateTx.id).toCompletableFuture().getOrThrow()
        assertEquals(updateTx.singleOutput(), updatedToken.singleOutput<House>())
    }

}
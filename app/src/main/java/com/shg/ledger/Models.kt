package com.shg.ledger


import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "shg_group")
data class SHGGroup(
    @PrimaryKey(autoGenerate = true)
    val groupId: Long = 0,
    val groupName: String,
    val groupCode: String?,
    val weeklySavingsAmount: Double = 100.0
)

@Entity(tableName = "members")
data class Member(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val groupId: Long = 1, // Default for now
    val memberCode: String,
    val name: String,
    val phone: String,
    val photoUrl: String? = null,
    val joinedDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "savings")
data class SavingsRecord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val memberId: String,
    val groupId: Long = 1,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val status: String,
    val weekNumber: Int
)

@Entity(tableName = "loans")
data class LoanRecord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val memberId: String,
    val groupId: Long = 1,
    val principalAmount: Double,
    val interestRate: Double, // Fixed 15 in UI logic, but stored
    val durationYears: Double, // New field for duration in years
    val totalWeeks: Int, // Number of weeks for repayment
    val weeklyEmi: Double, // Calculated weekly payment
    val repaidPrincipal: Double = 0.0,
    val repaidInterest: Double = 0.0,
    val status: String = "Active",
    val startDate: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "attendance")
data class AttendanceRecord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val memberId: String,
    val weekNumber: Int,
    val date: Long = System.currentTimeMillis(),
    val status: String
)

@Entity(tableName = "transactions")
data class TransactionRecord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val memberId: String,
    val type: String,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val referenceId: String? = null
)




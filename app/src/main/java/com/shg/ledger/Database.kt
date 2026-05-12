package com.shg.ledger


import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShgDao {
    // Member Operations
    @Query("SELECT * FROM members")
    fun getAllMembers(): Flow<List<Member>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: Member)

    @Delete
    suspend fun deleteMember(member: Member)

    // Savings Operations
    @Query("SELECT * FROM savings")
    fun getAllSavings(): Flow<List<SavingsRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavings(savings: SavingsRecord)

    // Loan Operations
    @Query("SELECT * FROM loans")
    fun getAllLoans(): Flow<List<LoanRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: LoanRecord)

    @Update
    suspend fun updateLoan(loan: LoanRecord)

    // Attendance Operations
    @Query("SELECT * FROM attendance")
    fun getAllAttendance(): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: AttendanceRecord)

    // Transaction Operations
    @Query("SELECT * FROM transactions")
    fun getAllTransactions(): Flow<List<TransactionRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionRecord)
}

@Dao
interface SHGGroupDao {
    @Query("SELECT * FROM shg_group LIMIT 1")
    fun getGroup(): Flow<SHGGroup?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: SHGGroup)
}

@Database(entities = [SHGGroup::class, Member::class, SavingsRecord::class, LoanRecord::class, AttendanceRecord::class, TransactionRecord::class], version = 7, exportSchema = false)
abstract class ShgDatabase : RoomDatabase() {
    abstract fun dao(): ShgDao
    abstract fun groupDao(): SHGGroupDao

    companion object {
        @Volatile
        private var INSTANCE: ShgDatabase? = null

        fun getDatabase(context: Context): ShgDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShgDatabase::class.java,
                    "shg_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}







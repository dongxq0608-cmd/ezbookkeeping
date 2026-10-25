package com.ezbook.mobile.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezbook.mobile.Graph
import com.ezbook.mobile.data.EzBookRepository
import com.ezbook.mobile.data.model.Account
import com.ezbook.mobile.data.model.CATEGORY_TYPE_EXPENSE
import com.ezbook.mobile.data.model.CATEGORY_TYPE_INCOME
import com.ezbook.mobile.data.model.CATEGORY_TYPE_TRANSFER
import com.ezbook.mobile.data.model.Category
import com.ezbook.mobile.data.model.TX_TYPE_BALANCE
import com.ezbook.mobile.data.model.TX_TYPE_EXPENSE
import com.ezbook.mobile.data.model.TX_TYPE_INCOME
import com.ezbook.mobile.data.model.TX_TYPE_TRANSFER
import com.ezbook.mobile.data.model.Tag
import com.ezbook.mobile.data.model.TransactionSaveRequest
import com.ezbook.mobile.util.AmountCalculator
import com.ezbook.mobile.util.Dates
import com.ezbook.mobile.util.Fmt
import com.ezbook.mobile.util.describeError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class TransactionEditUiState(
    val isNew: Boolean = true,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val type: Int = TX_TYPE_EXPENSE,
    val amountText: String = "",
    val categoryId: String = "0",
    val sourceAccountId: String = "",
    val destinationAccountId: String = "",
    val destinationAmountText: String = "",
    val time: Long = System.currentTimeMillis() / 1000,
    val utcOffset: Int = Dates.currentUtcOffsetMinutes(),
    val selectedTagIds: List<String> = emptyList(),
    val comment: String = "",
    val accountTree: List<Account> = emptyList(),
    val categoryTree: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val editable: Boolean = true,
    val error: String? = null,
    val finished: Boolean = false
) {
    val flatAccounts: List<Account>
        get() = EzBookRepository.flattenAccounts(accountTree)

    val availableCategories: List<Category>
        get() = when (type) {
            TX_TYPE_INCOME -> categoryTree.filter { it.type == CATEGORY_TYPE_INCOME }
            TX_TYPE_EXPENSE -> categoryTree.filter { it.type == CATEGORY_TYPE_EXPENSE }
            TX_TYPE_TRANSFER -> categoryTree.filter { it.type == CATEGORY_TYPE_TRANSFER }
            else -> emptyList()
        }

    val selectedCategory: Category?
        get() {
            if (categoryId == "0") {
                return null
            }

            fun find(list: List<Category>): Category? {
                list.forEach { item ->
                    if (item.id == categoryId) {
                        return item
                    }
                    val found = find(item.subCategories)
                    if (found != null) {
                        return found
                    }
                }
                return null
            }

            return find(categoryTree)
        }

    val sourceAccount: Account?
        get() = flatAccounts.firstOrNull { it.id == sourceAccountId }

    val destinationAccount: Account?
        get() = flatAccounts.firstOrNull { it.id == destinationAccountId }

    val currency: String
        get() = sourceAccount?.currency?.takeIf { it.isNotBlank() }
            ?: Graph.store.user?.defaultCurrency?.takeIf { it.isNotBlank() }
            ?: "CNY"

    val localDate: LocalDate
        get() = Dates.toLocalDate(time, utcOffset)

    val localTime: LocalTime
        get() = Dates.toLocalTime(time, utcOffset)
}

class TransactionEditViewModel : ViewModel() {

    private val _state = MutableStateFlow(TransactionEditUiState())
    val state: StateFlow<TransactionEditUiState> = _state.asStateFlow()

    private enum class Mode { NEW, EDIT, DUPLICATE }

    private var started = false
    private var mode = Mode.NEW
    private var sourceId: String = "0"
    private var duplicateWithTime = false

    fun start(transactionId: String) {
        if (started) return
        started = true
        sourceId = transactionId
        mode = if (transactionId.isBlank() || transactionId == "0") Mode.NEW else Mode.EDIT
        _state.update { it.copy(isNew = mode == Mode.NEW, loading = true) }
        load()
    }

    /**
     * 复制成一笔新账单（对应网页端两个复制功能）：
     * - withTime = false：时间改为当前时间
     * - withTime = true ：保留原交易时间
     */
    fun startDuplicate(transactionId: String, withTime: Boolean) {
        if (started) return
        started = true
        sourceId = transactionId
        mode = Mode.DUPLICATE
        duplicateWithTime = withTime
        _state.update { it.copy(isNew = true, loading = true) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val accounts = runCatching { Graph.repo.accounts() }.getOrDefault(emptyList())
                val categories = runCatching { Graph.repo.categories() }.getOrDefault(emptyList())
                val tags = runCatching { Graph.repo.tags() }.getOrDefault(emptyList())

                _state.update {
                    it.copy(accountTree = accounts, categoryTree = categories, tags = tags)
                }

                if (mode == Mode.NEW) {
                    fillDefaults()
                    _state.update { it.copy(loading = false) }
                } else {
                    val transaction = Graph.repo.transaction(sourceId)
                    val keepSourceTime = mode == Mode.EDIT || duplicateWithTime
                    _state.update {
                        it.copy(
                            loading = false,
                            editable = if (mode == Mode.EDIT) transaction.editable else true,
                            type = transaction.type,
                            amountText = if (transaction.type == TX_TYPE_BALANCE) {
                                Fmt.moneyPlain(transaction.sourceAmount)
                            } else {
                                Fmt.moneyPlain(kotlin.math.abs(transaction.sourceAmount))
                            },
                            categoryId = transaction.categoryId,
                            sourceAccountId = transaction.sourceAccountId,
                            destinationAccountId = transaction.destinationAccountId ?: "",
                            destinationAmountText = transaction.destinationAmount?.let { amount ->
                                Fmt.moneyPlain(kotlin.math.abs(amount))
                            } ?: "",
                            time = if (keepSourceTime) transaction.time else System.currentTimeMillis() / 1000,
                            utcOffset = if (keepSourceTime) transaction.utcOffset else Dates.currentUtcOffsetMinutes(),
                            selectedTagIds = transaction.tagIds,
                            comment = transaction.comment
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = describeError(e)) }
            }
        }
    }

    private fun fillDefaults() {
        val accounts = _state.value.flatAccounts

        // 默认选中上一次记账用过的账户；没有记录时取账户列表中的第一个
        val lastUsed = Graph.store.lastAccountId?.takeIf { id -> accounts.any { it.id == id } }
        val defaultAccountId = lastUsed ?: accounts.firstOrNull()?.id ?: ""

        val firstCategory = _state.value.availableCategories.firstOrNull()?.let { parent ->
            parent.subCategories.firstOrNull()?.id ?: parent.id
        } ?: "0"

        _state.update {
            it.copy(
                sourceAccountId = defaultAccountId,
                categoryId = firstCategory,
                time = System.currentTimeMillis() / 1000,
                utcOffset = Dates.currentUtcOffsetMinutes()
            )
        }
    }

    fun setType(type: Int) {
        _state.update { current ->
            if (current.type == type) {
                return@update current
            }

            val firstCategory = when (type) {
                TX_TYPE_EXPENSE, TX_TYPE_INCOME -> {
                    val list = current.copy(type = type).availableCategories
                    list.firstOrNull()?.let { parent ->
                        parent.subCategories.firstOrNull()?.id ?: parent.id
                    } ?: "0"
                }

                else -> "0"
            }

            current.copy(
                type = type,
                categoryId = firstCategory,
                destinationAccountId = if (type == TX_TYPE_TRANSFER) current.destinationAccountId else "",
                destinationAmountText = if (type == TX_TYPE_TRANSFER) current.destinationAmountText else ""
            )
        }
    }

    fun setAmount(text: String) {
        val allowNegative = _state.value.type == TX_TYPE_BALANCE
        val filtered = sanitizeAmountInput(text, allowNegative)
        _state.update { it.copy(amountText = filtered, error = null) }
    }

    fun setDestinationAmount(text: String) {
        val filtered = sanitizeAmountInput(text, false)
        _state.update { it.copy(destinationAmountText = filtered, error = null) }
    }

    fun setCategory(categoryId: String) {
        _state.update { it.copy(categoryId = categoryId) }
    }

    fun setSourceAccount(accountId: String) {
        _state.update {
            it.copy(
                sourceAccountId = accountId,
                destinationAccountId = if (it.destinationAccountId == accountId) "" else it.destinationAccountId
            )
        }
    }

    fun setDestinationAccount(accountId: String) {
        _state.update { it.copy(destinationAccountId = accountId) }
    }

    fun setDateTime(date: LocalDate, time: LocalTime) {
        _state.update {
            it.copy(
                time = Dates.toEpochSeconds(date, time),
                utcOffset = Dates.currentUtcOffsetMinutes()
            )
        }
    }

    fun toggleTag(tagId: String) {
        _state.update {
            val ids = it.selectedTagIds.toMutableList()
            if (ids.contains(tagId)) {
                ids.remove(tagId)
            } else if (ids.size < 10) {
                ids.add(tagId)
            }
            it.copy(selectedTagIds = ids)
        }
    }

    fun setComment(text: String) {
        _state.update { it.copy(comment = text.take(255)) }
    }

    fun save() {
        val current = _state.value
        if (current.saving) return

        val amount = AmountCalculator.evaluate(current.amountText) ?: 0L
        if (current.type == TX_TYPE_BALANCE) {
            if (amount == 0L) {
                _state.update { it.copy(error = "请输入变更金额（可为负数）") }
                return
            }
        } else if (amount <= 0L) {
            _state.update { it.copy(error = "请输入大于 0 的金额") }
            return
        }
        if (current.type != TX_TYPE_BALANCE && current.categoryId == "0") {
            _state.update { it.copy(error = "请选择分类") }
            return
        }
        if (current.sourceAccountId.isBlank()) {
            _state.update { it.copy(error = "请选择账户") }
            return
        }
        if (current.type == TX_TYPE_TRANSFER) {
            if (current.destinationAccountId.isBlank()) {
                _state.update { it.copy(error = "请选择转入账户") }
                return
            }
            if (current.destinationAccountId == current.sourceAccountId) {
                _state.update { it.copy(error = "转出账户与转入账户不能相同") }
                return
            }
        }

        val destinationAmount = if (current.type == TX_TYPE_TRANSFER) {
            AmountCalculator.evaluate(current.destinationAmountText)?.takeIf { it > 0 } ?: amount
        } else {
            0L
        }

        val request = TransactionSaveRequest(
            id = if (current.isNew) null else sourceId,
            type = current.type,
            categoryId = if (current.type == TX_TYPE_BALANCE) "0" else current.categoryId,
            time = current.time,
            utcOffset = current.utcOffset,
            sourceAccountId = current.sourceAccountId,
            destinationAccountId = if (current.type == TX_TYPE_TRANSFER) current.destinationAccountId else "0",
            sourceAmount = amount,
            destinationAmount = destinationAmount,
            tagIds = current.selectedTagIds,
            comment = current.comment.trim(),
            clientSessionId = if (current.isNew) UUID.randomUUID().toString() else null
        )

        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                Graph.repo.save(request, current.isNew)
                // 记住这次用的账户，下次记账默认选中它
                Graph.store.lastAccountId = current.sourceAccountId
                _state.update { it.copy(saving = false, finished = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = describeError(e)) }
            }
        }
    }

    fun delete() {
        val current = _state.value
        if (current.isNew || current.saving) return

        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                Graph.repo.deleteTransaction(sourceId)
                _state.update { it.copy(saving = false, finished = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = describeError(e)) }
            }
        }
    }

    private companion object {
        const val ALLOWED_AMOUNT_CHARS = "0123456789.+-*/()×÷"
        const val MAX_AMOUNT_LENGTH = 40
    }

    /** 允许输入数字与四则运算符，由 AmountCalculator 负责求值 */
    private fun sanitizeAmountInput(text: String, allowNegative: Boolean): String {
        var result = text.filter { ALLOWED_AMOUNT_CHARS.contains(it) }
        if (!allowNegative) {
            result = result.replace("-", "")
        }
        return result.take(MAX_AMOUNT_LENGTH)
    }
}

/*
 * Copyright 2014-2022 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#ifndef AERON_CONCURRENT_ATOMIC64_GCC_X86_64_H
#define AERON_CONCURRENT_ATOMIC64_GCC_X86_64_H

#include <cstdint>
#include <atomic>

namespace aeron { namespace concurrent { namespace atomic {

inline void thread_fence()
{
    std::atomic_thread_fence(std::memory_order_acq_rel);
}

inline void fence()
{
    std::atomic_thread_fence(std::memory_order_seq_cst);
}

inline void acquire()
{
#if (!defined(__clang__) && defined(__GNUC__) && __GNUC__ < 8)
    volatile std::int64_t *dummy;
    asm volatile("movq 0(%%rsp), %0" : "=r" (dummy) :: "memory");
#else
    std::atomic_thread_fence(std::memory_order_acquire);
#endif
}

inline void release()
{
    std::atomic_thread_fence(std::memory_order_release);
}

inline void cpu_pause()
{
    asm volatile("pause\n" ::: "memory");
}

inline std::int32_t getInt32Volatile(volatile std::int32_t *source)
{
    std::int32_t sequence = *reinterpret_cast<volatile std::int32_t *>(source);
    acquire();

    return sequence;
}

inline void putInt32Volatile(volatile std::int32_t *address, std::int32_t value)
{
    asm volatile("xchgl (%2), %0"
        : "=r" (value)
        : "0" (value), "r" (address)
        : "memory");
}

inline void putInt32Ordered(volatile std::int32_t *address, std::int32_t value)
{
    release();
    *reinterpret_cast<volatile std::int32_t *>(address) = value;
}

inline void putInt32Atomic(volatile std::int32_t *address, std::int32_t value)
{
    asm volatile("xchgl (%2), %0"
        : "=r" (value)
        : "0" (value), "r" (address)
        : "memory");
}

inline std::int64_t getInt64Volatile(volatile std::int64_t *source)
{
    std::int64_t sequence = *reinterpret_cast<volatile std::int64_t *>(source);
    acquire();

    return sequence;
}

inline void putInt64Volatile(volatile std::int64_t *address, std::int64_t value)
{
    asm volatile("xchgq (%2), %0"
        : "=r" (value)
        : "0" (value), "r" (address)
        : "memory");
}

inline void putInt64Ordered(volatile std::int64_t *address, std::int64_t value)
{
    release();
    *reinterpret_cast<volatile std::int64_t *>(address) = value;
}

inline void putInt64Atomic(volatile std::int64_t *address, std::int64_t value)
{
    asm volatile("xchgq (%2), %0"
        : "=r" (value)
        : "0" (value), "r" (address)
        : "memory");
}

inline std::int64_t getAndAddInt64(volatile std::int64_t *address, std::int64_t value)
{
    std::int64_t original;
    asm volatile("lock; xaddq %0, %1"
        : "=r"(original), "+m"(*address)
        : "0"(value));

    return original;
}

inline std::int32_t getAndAddInt32(volatile std::int32_t *address, std::int32_t value)
{
    std::int32_t original;
    asm volatile("lock; xaddl %0, %1"
        : "=r" (original), "+m" (*address)
        : "0" (value));

    return original;
}

inline std::int32_t xchg(volatile std::int32_t *address, std::int32_t value)
{
    asm volatile("xchgl %1, %0"
        : "=r" (value)
        : "m" (*address), "0" (value)
        : "memory");

    return value;
}

inline std::int64_t xchg(volatile std::int64_t *address, std::int64_t value)
{
    asm volatile("xchgq %1, %0"
        : "=r" (value)
        : "m" (*address), "0" (value)
        : "memory");

    return value;
}

inline std::int32_t cmpxchg(volatile std::int32_t *address, std::int32_t expected, std::int32_t desired)
{
    std::int32_t original;
    asm volatile("lock; cmpxchgl %2, %1"
        : "=a" (original), "+m" (*address)
        : "q" (desired), "0" (expected));

    return original;
}

inline std::int64_t cmpxchg(volatile std::int64_t *address, std::int64_t expected, std::int64_t desired)
{
    std::int64_t original;
    asm volatile("lock; cmpxchgq %2, %1"
        : "=a" (original), "+m" (*address)
        : "q" (desired), "0" (expected));

    return original;
}

//-------------------------------------
//  Alignment
//-------------------------------------
// Note: May not work on local variables.
// http://gcc.gnu.org/bugzilla/show_bug.cgi?id=24691
#define AERON_DECL_ALIGNED(declaration, amt) declaration __attribute__((aligned(amt)))

}}}

#endif

/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _IGNITE_IMPL_BINARY_BINARY_TYPE_HANDLER
#define _IGNITE_IMPL_BINARY_BINARY_TYPE_HANDLER

#include <ignite/common/concurrent.h>

#include "ignite/impl/binary/binary_type_snapshot.h"

namespace ignite
{    
    namespace impl
    {
        namespace binary
        {
            /**
             * Type handler. Tracks all type updates during write session.
             */
            class BinaryTypeHandler
            {
            public:

                /**
                 * Constructor.
                 *
                 * @param snap Snapshot.
                 */
                BinaryTypeHandler(SPSnap snap);

                /**
                 * Callback invoked when field is being written.
                 *
                 * @param fieldId Field ID.
                 * @param fieldName Field name.
                 * @param fieldTypeId Field type ID.
                 */
                void OnFieldWritten(int32_t fieldId, std::string fieldName, int32_t fieldTypeId);

                /**
                 * Whether any difference exists.
                 *
                 * @return True if difference exists.
                 */
                bool HasUpdate() const
                {
                    return updated.Get() != 0;
                }

                /**
                 * Get updated snapshot.
                 *
                 * @return Updated snapshot.
                 */
                SPSnap GetUpdated()
                {
                    return updated;
                }

            private:
                /** Snapshot. */
                SPSnap origin;

                /** Snapshot. */
                SPSnap updated;

                IGNITE_NO_COPY_ASSIGNMENT(BinaryTypeHandler);
            };
        }
    }    
}

#endif //_IGNITE_IMPL_BINARY_BINARY_TYPE_HANDLER

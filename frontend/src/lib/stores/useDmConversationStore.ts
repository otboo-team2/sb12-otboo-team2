import { create } from 'zustand';
import type { DmConversationDto, CursorParams } from '@/lib/api/types';
import { getDmConversations } from '@/lib/api/messages';
import { type PaginatedStore } from './types';
import { createPaginatedStoreActions } from '@/lib/stores/actions.ts';

interface DmConversationStore extends PaginatedStore<DmConversationDto, CursorParams> {}

export const useDmConversationStore = create<DmConversationStore>((set, get) => ({
    ...createPaginatedStoreActions({
        set, get,
        fetchApi: getDmConversations,
        initialData: {
            params: { cursor: undefined, idAfter: undefined, limit: 20 },
        },
        keyExtractor: (e) => e.messageId,
    }),
}));

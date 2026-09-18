import { create } from 'zustand';

interface ActiveDmStore {
    activePartnerId: string | null;
    setActivePartnerId: (id: string | null) => void;
}

export const useActiveDmStore = create<ActiveDmStore>((set) => ({
    activePartnerId: null,
    setActivePartnerId: (id) => set({ activePartnerId: id }),
}));

import { useState, useEffect, useCallback } from 'react';
import { api, pageContent } from './api';

/**
 * Hook dùng chung để lấy danh sách ngựa được phép truy cập theo role.
 * - HORSE_OWNER: /api/horses/mine
 * - HEAD_TRAINER: /api/horses/trainer-assigned
 * - CLUB_MANAGER & others: /api/horses
 */
export function useMyHorses(user, canEdit = false) {
    const [horses, setHorses] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const load = useCallback(async () => {
        if (!user || !user.roles) {
            setLoading(false);
            return;
        }
        
        setLoading(true);
        setError(null);
        try {
            let path = '/api/horses?size=100';
            
            const isManager = user.roles.includes('CLUB_MANAGER') || canEdit;
            const isOwner = user.roles.includes('HORSE_OWNER');
            const isTrainer = user.roles.includes('HEAD_TRAINER');
            
            if (!isManager) {
                if (isOwner) {
                    path = '/api/horses/mine?size=100';
                } else if (isTrainer) {
                    path = '/api/horses/trainer-assigned?size=100';
                }
            }
            
            const data = await api(path);
            setHorses(pageContent(data));
        } catch (err) {
            setError(err);
            setHorses([]);
        } finally {
            setLoading(false);
        }
    }, [user, canEdit]);

    useEffect(() => {
        load();
    }, [load]);

    return { horses, loading, error, reload: load };
}
